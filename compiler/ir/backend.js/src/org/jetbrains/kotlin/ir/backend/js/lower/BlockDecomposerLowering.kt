/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.backend.common.DeclarationContainerLoweringPass
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder
import org.jetbrains.kotlin.ir.backend.js.symbols.JsSymbolBuilder
import org.jetbrains.kotlin.ir.backend.js.symbols.initialize
import org.jetbrains.kotlin.ir.backend.js.utils.Namer
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.util.transformFlat
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.types.KotlinType

class BlockDecomposerLowering(val context: JsIrBackendContext) : DeclarationContainerLoweringPass {
    private lateinit var function: IrFunction
    private var tmpVarCounter: Int = 0

    private val statementTransformer = StatementTransformer()
    private val expressionTransformer = ExpressionTransformer()

    private val constTrue = JsIrBuilder.buildBoolean(context.builtIns.booleanType, true)
    private val constFalse = JsIrBuilder.buildBoolean(context.builtIns.booleanType, false)
    private val nothingType = context.builtIns.nullableNothingType

    private val unitType = context.builtIns.unitType
    private val unitValue = JsIrBuilder.buildGetObjectValue(unitType, context.symbolTable.referenceClass(context.builtIns.unit))

    private val unreachableFunction =
        JsSymbolBuilder.buildSimpleFunction(context.module, Namer.UNREACHABLE_NAME).initialize(type = nothingType)

    override fun lower(irDeclarationContainer: IrDeclarationContainer) {
        irDeclarationContainer.declarations.transformFlat { declaration ->
            when (declaration) {
                is IrFunction -> {
                    lower(declaration)
                    listOf(declaration)
                }
                is IrField -> lower(declaration, irDeclarationContainer)
                else -> listOf(declaration)
            }
        }
    }

    fun lower(irFunction: IrFunction) {
        function = irFunction
        tmpVarCounter = 0
        irFunction.body = irFunction.body?.accept(statementTransformer, null) as? IrBody
    }

    fun lower(irField: IrField, container: IrDeclarationContainer): List<IrDeclaration> {
        irField.initializer?.apply {
            val initFnSymbol = JsSymbolBuilder.buildSimpleFunction(
                (container as IrSymbolOwner).symbol.descriptor,
                irField.name.asString() + "\$init\$"
            ).initialize(type = expression.type)


            val returnStatement = JsIrBuilder.buildReturn(initFnSymbol, expression)
            val newBody = IrBlockBodyImpl(expression.startOffset, expression.endOffset).apply {
                statements += returnStatement
            }

            val initFn = JsIrBuilder.buildFunction(initFnSymbol).apply {
                body = newBody
            }

            lower(initFn)

            val lastStatement = newBody.statements.last()
            if (lastStatement != returnStatement || (lastStatement as IrReturn).value != expression) {
                expression = JsIrBuilder.buildCall(initFnSymbol)
                return listOf(initFn, irField)
            }
        }

        return listOf(irField)
    }

    private fun processStatements(statements: MutableList<IrStatement>) {
        statements.transformFlat {
            uncastComposite(it.transform(statementTransformer, null))
        }
    }

    private fun makeTempVar(type: KotlinType) =
        JsSymbolBuilder.buildTempVar(function.symbol, type, "tmp\$dcms\$${tmpVarCounter++}", true)

    private fun makeLoopLabel() = "\$l\$${tmpVarCounter++}"

    private fun IrStatement.asExpression(last: IrExpression): IrExpression {
        val composite = JsIrBuilder.buildComposite(last.type)
        composite.statements += transform(statementTransformer, null)
        composite.statements += last
        return composite
    }

    private fun materializeExpression(composite: IrComposite, block: (IrExpression) -> IrStatement): IrComposite {
        val statements = composite.statements
        val expression = statements.lastOrNull() as? IrExpression ?: return composite
        statements[statements.size - 1] = block(expression)
        return composite
    }

    private fun uncastComposite(expression: IrStatement) = (expression as? IrComposite)?.statements ?: listOf(expression)

    private inner class BreakContinueUpdater(val breakLoop: IrLoop, val continueLoop: IrLoop) : IrElementTransformer<IrLoop> {
        override fun visitBreak(jump: IrBreak, data: IrLoop) = jump.apply {
            if (loop == data) loop = breakLoop
        }

        override fun visitContinue(jump: IrContinue, data: IrLoop) = jump.apply {
            if (loop == data) loop = continueLoop
        }
    }

    private inner class StatementTransformer : IrElementTransformerVoid() {
        override fun visitBlockBody(body: IrBlockBody) = body.apply { processStatements(statements) }

        override fun visitContainerExpression(expression: IrContainerExpression) = expression.apply { processStatements(statements) }

        override fun visitExpression(expression: IrExpression) = expression.transform(expressionTransformer, null)

        override fun visitReturn(expression: IrReturn): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val composite = expression.value as? IrComposite ?: return expression
            return materializeExpression(composite) {
                IrReturnImpl(expression.startOffset, expression.endOffset, expression.type, expression.returnTargetSymbol, it)
            }
        }

        override fun visitThrow(expression: IrThrow): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val composite = expression.value as? IrComposite ?: return expression
            return materializeExpression(composite) {
                IrThrowImpl(expression.startOffset, expression.endOffset, expression.type, it)
            }
        }

        override fun visitBreakContinue(jump: IrBreakContinue) = jump

        override fun visitVariable(declaration: IrVariable): IrStatement {
            declaration.transformChildrenVoid(expressionTransformer)

            val composite = declaration.initializer as? IrComposite ?: return declaration
            return materializeExpression(composite) {
                declaration.apply { initializer = it }
            }
        }

        override fun visitSetField(expression: IrSetField): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val receiverResult = expression.receiver as? IrComposite
            val valueResult = expression.value as? IrComposite
            if (receiverResult == null && valueResult == null) return expression

            if (receiverResult != null && valueResult != null) {
                val result = IrCompositeImpl(receiverResult.startOffset, expression.endOffset, unitType)
                val receiverValue = receiverResult.statements.last() as IrExpression
                val tmp = makeTempVar(receiverResult.type)
                val irVar = JsIrBuilder.buildVar(tmp, receiverValue)
                val setValue = valueResult.statements.last() as IrExpression
                result.statements += receiverResult.statements.dropLast(1)
                result.statements += irVar
                result.statements += valueResult.statements.dropLast(1)
                result.statements += expression.run {
                    IrSetFieldImpl(
                        startOffset,
                        endOffset,
                        symbol,
                        JsIrBuilder.buildGetValue(tmp),
                        setValue,
                        origin,
                        superQualifierSymbol
                    )
                }
                return result
            }

            if (receiverResult != null) {
                return materializeExpression(receiverResult) {
                    expression.run { IrSetFieldImpl(startOffset, endOffset, symbol, it, value, origin, superQualifierSymbol) }
                }
            }

            assert(valueResult != null)

            val receiver = expression.receiver?.let {
                val tmp = makeTempVar(it.type)
                val irVar = JsIrBuilder.buildVar(tmp, it)
                valueResult!!.statements.add(0, irVar)
                JsIrBuilder.buildGetValue(tmp)
            }

            return materializeExpression(valueResult!!) {
                expression.run { IrSetFieldImpl(startOffset, endOffset, symbol, receiver, it, origin, superQualifierSymbol) }
            }
        }

        override fun visitSetVariable(expression: IrSetVariable): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val composite = expression.value as? IrComposite ?: return expression

            return materializeExpression(composite) {
                expression.run { IrSetVariableImpl(startOffset, endOffset, symbol, it, origin) }
            }
        }

        // while (c_block {}) {
        //  body {}
        // }
        //
        // is transformed into
        //
        // while (true) {
        //   var cond = c_block {}
        //   if (!cond) break
        //   body {}
        // }
        //
        override fun visitWhileLoop(loop: IrWhileLoop): IrExpression {
            val newBody = loop.body?.transform(statementTransformer, null)
            val newCondition = loop.condition.transform(expressionTransformer, null)

            if (newCondition is IrComposite) {
                val newLoopBody = IrBlockImpl(loop.startOffset, loop.endOffset, loop.type, loop.origin)

                newLoopBody.statements += newCondition.statements.dropLast(1)
                val thenBlock = JsIrBuilder.buildBlock(unitType, listOf(JsIrBuilder.buildBreak(unitType, loop)))

                val newLoopCondition = newCondition.statements.last() as IrExpression

                val breakCond = JsIrBuilder.buildCall(context.irBuiltIns.booleanNotSymbol).apply {
                    putValueArgument(0, newLoopCondition)
                }

                newLoopBody.statements += JsIrBuilder.buildIfElse(unitType, breakCond, thenBlock)
                newLoopBody.statements += newBody!!

                val newLoop = IrWhileLoopImpl(loop.startOffset, loop.endOffset, loop.type, loop.origin)

                return newLoop.apply {
                    label = loop.label
                    condition = constTrue
                    body = newLoopBody.transform(BreakContinueUpdater(newLoop, newLoop), loop)
                }
            }

            return loop.apply {
                body = newBody
                condition = newCondition
            }
        }

        // do  {
        //  body {}
        // } while (c_block {})
        //
        // is transformed into
        //
        // do {
        //   do {
        //     body {}
        //   } while (false)
        //   cond = c_block {}
        // } while (cond)
        //
        override fun visitDoWhileLoop(loop: IrDoWhileLoop): IrExpression {
            val newBody = loop.body?.transform(statementTransformer, null)!!
            val newCondition = loop.condition.transform(expressionTransformer, null)

            if (newCondition is IrComposite) {
                val innerLoop = IrDoWhileLoopImpl(loop.startOffset, loop.endOffset, unitType, loop.origin, newBody, constFalse).apply {
                    label = makeLoopLabel()
                }

                val newLoopCondition = newCondition.statements.last() as IrExpression
                val newLoopBody = IrBlockImpl(newCondition.startOffset, newBody.endOffset, newBody.type).apply {
                    statements += innerLoop
                    statements += newCondition.statements.dropLast(1)
                }

                val newLoop = IrDoWhileLoopImpl(loop.startOffset, loop.endOffset, unitType, loop.origin)

                return newLoop.apply {
                    condition = newLoopCondition
                    body = newLoopBody.transform(BreakContinueUpdater(newLoop, innerLoop), loop)
                    label = loop.label ?: makeLoopLabel()
                }
            }

            return loop.apply {
                body = newBody
                condition = newCondition
            }
        }

        // when {
        //  c1_block {} -> b1_block {}
        //  ....
        //  cn_block {} -> bn_block {}
        //  else -> else_block {}
        // }
        //
        // transformed into if-else chain
        // c1 = c1_block {}
        // if (c1) {
        //   b1_block {}
        // } else {
        //   c2 = c2_block {}
        //   if (c2) {
        //     b2_block{}
        //   } else {
        //         ...
        //           else {
        //              else_block {}
        //           }
        // }
        override fun visitWhen(expression: IrWhen): IrExpression {

            var compositeCount = 0

            val results = expression.branches.map {
                val cond = it.condition.transform(expressionTransformer, null)
                val res = it.result.transform(statementTransformer, null)
                if (cond is IrComposite) compositeCount++
                Triple(cond, res, it)
            }

            if (compositeCount == 0) {
                val branches = results.map { (cond, res, orig) ->
                    when (orig) {
                        is IrElseBranch -> IrElseBranchImpl(orig.startOffset, orig.endOffset, cond, res)
                        else /* IrBranch */ -> IrBranchImpl(orig.startOffset, orig.endOffset, cond, res)
                    }
                }
                return expression.run { IrWhenImpl(startOffset, endOffset, type, origin, branches) }
            }

            val block = IrBlockImpl(expression.startOffset, expression.endOffset, unitType, expression.origin)

            results.fold(block) { appendBlock, (cond, res, orig) ->
                val condStatements = uncastComposite(cond)
                val condValue = condStatements.last() as IrExpression

                appendBlock.statements += condStatements.dropLast(1)

                JsIrBuilder.buildBlock(unitType).also {
                    val elseBlock = if (orig is IrElseBranch) null else it

                    val ifElseNode = IrIfThenElseImpl(
                        orig.startOffset,
                        orig.endOffset,
                        unitType,
                        condValue,
                        res,
                        elseBlock,
                        expression.origin
                    )
                    appendBlock.statements += ifElseNode
                }
            }

            return block
        }
    }

    private inner class ExpressionTransformer : IrElementTransformerVoid() {

        override fun visitExpression(expression: IrExpression) = expression.transformChildren()

        override fun visitGetField(expression: IrGetField): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val composite = expression.receiver as? IrComposite ?: return expression

            return materializeExpression(composite) {
                expression.run { IrGetFieldImpl(startOffset, endOffset, symbol, it, origin, superQualifierSymbol) }
            }
        }

        override fun visitGetClass(expression: IrGetClass): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val composite = expression.argument as? IrComposite ?: return expression

            return materializeExpression(composite) {
                expression.run { IrGetClassImpl(startOffset, endOffset, type, it) }
            }
        }

        override fun visitLoop(loop: IrLoop) = loop.asExpression(unitValue)

        override fun visitSetVariable(expression: IrSetVariable) = expression.asExpression(unitValue)

        override fun visitSetField(expression: IrSetField) = expression.asExpression(unitValue)

        override fun visitBreakContinue(jump: IrBreakContinue) = jump.asExpression(JsIrBuilder.buildCall(unreachableFunction))

        override fun visitThrow(expression: IrThrow) = expression.asExpression(JsIrBuilder.buildCall(unreachableFunction))

        override fun visitReturn(expression: IrReturn) = expression.asExpression(JsIrBuilder.buildCall(unreachableFunction))

        override fun visitStringConcatenation(expression: IrStringConcatenation): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val compositeCount = expression.arguments.fold(0) { r, t -> if (t is IrComposite) r + 1 else r }

            if (compositeCount == 0) return expression

            val newStatements = mutableListOf<IrStatement>()
            val arguments = mapArguments(expression.arguments, compositeCount, newStatements)

            newStatements += expression.run { IrStringConcatenationImpl(startOffset, endOffset, type, arguments.map { it!! }) }
            return JsIrBuilder.buildComposite(expression.type, newStatements)
        }

        private fun mapArguments(
            oldArguments: Collection<IrExpression?>,
            compositeCount: Int,
            newStatements: MutableList<IrStatement>
        ): List<IrExpression?> {
            var compositesLeft = compositeCount
            val arguments = mutableListOf<IrExpression?>()

            for (arg in oldArguments) {
                val value = if (arg is IrComposite) {
                    compositesLeft--
                    newStatements += arg.statements.dropLast(1)
                    arg.statements.last() as IrExpression
                } else arg

                val newArg = if (compositesLeft != 0) {
                    if (value != null) {
                        val tmp = makeTempVar(value.type)
                        newStatements += JsIrBuilder.buildVar(tmp, value)
                        JsIrBuilder.buildGetValue(tmp)
                    } else value
                } else value

                arguments += newArg
            }
            return arguments
        }

        override fun visitVararg(expression: IrVararg): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val compositeCount = expression.elements.fold(0) { r, t -> if (t is IrComposite) r + 1 else r }

            if (compositeCount == 0) return expression

            val newStatements = mutableListOf<IrStatement>()
            val argumentsExpressions = mapArguments(
                expression.elements.map { (it as? IrSpreadElement)?.expression ?: it as IrExpression },
                compositeCount,
                newStatements
            )

            val arguments = expression.elements.withIndex().map { (i, v) ->
                val expr = argumentsExpressions[i]!!
                (v as? IrSpreadElement)?.run { IrSpreadElementImpl(startOffset, endOffset, expr) } ?: expr
            }

            newStatements += expression.run { IrVarargImpl(startOffset, endOffset, type, varargElementType, arguments) }
            return expression.run { IrCompositeImpl(startOffset, endOffset, type, null, newStatements) }
        }

        // The point here is to keep original evaluation order so (there is the same story for StringConcat)
        // d.foo(p1, p2, block {}, p4, block {}, p6, p7)
        //
        // is transformed into
        //
        // var d_tmp = d
        // var p1_tmp = p1
        // var p2_tmp = p2
        // var p3_tmp = block {}
        // var p4_tmp = p4
        // var p5_tmp = block {}
        // d_tmp.foo(p1_tmp, p2_tmp, p3_tmp, p4_tmp, p5_tmp, p6, p7)
        override fun visitMemberAccess(expression: IrMemberAccessExpression): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val oldArguments = mutableListOf(expression.dispatchReceiver, expression.extensionReceiver)
            for (i in 0 until expression.valueArgumentsCount) oldArguments += expression.getValueArgument(i)
            val compositeCount = oldArguments.fold(0) { r, t -> if (t is IrComposite) r + 1 else r }

            if (compositeCount == 0) return expression

            val newStatements = mutableListOf<IrStatement>()
            val newArguments = mapArguments(oldArguments, compositeCount, newStatements)

            expression.dispatchReceiver = newArguments[0]
            expression.extensionReceiver = newArguments[1]

            for (i in 0 until expression.valueArgumentsCount) {
                expression.putValueArgument(i, newArguments[i + 2])
            }

            newStatements += expression

            return expression.run { IrCompositeImpl(startOffset, endOffset, type, origin, newStatements) }
        }

        override fun visitContainerExpression(expression: IrContainerExpression): IrExpression {

            expression.run { if (statements.isEmpty()) return IrCompositeImpl(startOffset, endOffset, type, origin, emptyList()) }

            val newStatements = mutableListOf<IrStatement>()

            for (i in 0 until expression.statements.size - 1) {
                newStatements += uncastComposite(expression.statements[i].transform(statementTransformer, null))
            }

            newStatements += uncastComposite(expression.statements.last().transform(expressionTransformer, null))

            return JsIrBuilder.buildComposite(expression.type, newStatements)
        }

        private fun wrap(expression: IrExpression) =
            expression as? IrBlock ?: expression.let { IrBlockImpl(it.startOffset, it.endOffset, it.type, null, listOf(it)) }

        private fun wrap(expression: IrExpression, variable: IrVariableSymbol) = wrap(JsIrBuilder.buildSetVariable(variable, expression))

        // try {
        //   try_block {}
        // } catch () {
        //   catch_block {}
        // } finally {}
        //
        // transformed into if-else chain
        //
        // Composite [
        //   var tmp
        //   try {
        //     tmp = try_block {}
        //   } catch () {
        //     tmp = catch_block {}
        //   } finally {}
        //   tmp
        // ]
        override fun visitTry(aTry: IrTry): IrExpression {
            val tmp = makeTempVar(aTry.type)

            val newTryResult = wrap(aTry.tryResult, tmp)
            val newCatches = aTry.catches.map {
                val newCatchBody = wrap(it.result, tmp)
                IrCatchImpl(it.startOffset, it.endOffset, it.catchParameter, newCatchBody)
            }

            val newTry = aTry.run { IrTryImpl(startOffset, endOffset, unitType, newTryResult, newCatches, finallyExpression) }
            newTry.transformChildrenVoid(statementTransformer)

            return JsIrBuilder.buildComposite(aTry.type, listOf(JsIrBuilder.buildVar(tmp), newTry, JsIrBuilder.buildGetValue(tmp)))
        }

        // when {
        //  c1 -> b1_block {}
        //  ....
        //  cn -> bn_block {}
        //  else -> else_block {}
        // }
        //
        // transformed into if-else chain
        //
        // Composite [
        //   var tmp
        //   when {
        //     c1 -> tmp = b1_block {}
        //     ...
        //     cn -> tmp = bn_block {}
        //     else -> tmp = else_block {}
        //   }
        //   tmp
        // ]

        override fun visitWhen(expression: IrWhen): IrExpression {
            val tmp = makeTempVar(expression.type)

            val newBranches = expression.branches.map {
                val newResult = wrap(it.result, tmp)
                when (it) {
                    is IrElseBranch -> IrElseBranchImpl(it.startOffset, it.endOffset, it.condition, newResult)
                    else /* IrBranch */ -> IrBranchImpl(it.startOffset, it.endOffset, it.condition, newResult)
                }
            }

            val irVar = JsIrBuilder.buildVar(tmp)
            val newWhen =
                expression.run { IrWhenImpl(startOffset, endOffset, unitType, origin, newBranches) }.transform(statementTransformer, null)

            return JsIrBuilder.buildComposite(expression.type, listOf(irVar, newWhen, JsIrBuilder.buildGetValue(tmp)))
        }
    }
}