/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.backend.common.peek
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder
import org.jetbrains.kotlin.ir.backend.js.symbols.JsSymbolBuilder
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrExpressionBase
import org.jetbrains.kotlin.ir.expressions.impl.IrSetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrStringConcatenationImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSuspensionPointImpl
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.visitors.*
import org.jetbrains.kotlin.types.KotlinType


object COROUTINE_ROOT_LOOP : IrStatementOriginImpl("COROUTINE_ROOT_LOOP")
object COROUTINE_SWITCH : IrStatementOriginImpl("COROUTINE_SWITCH")

class SuspendPointTransformer : IrElementTransformerVoid() {
    override fun visitCall(expression: IrCall) = expression.let {
        it.transformChildrenVoid(this)
        if (it.descriptor.isSuspend) JsIrBuilder.buildComposite(it.type, listOf(IrSuspensionPointImpl(it))) else it
    }
}

open class SuspendableNodesCollector(protected val suspendableNodes: MutableSet<IrElement>) : IrElementVisitorVoid {

    protected var hasSuspendableChildren = false

    override fun visitElement(element: IrElement) {
        val current = hasSuspendableChildren
        hasSuspendableChildren = false
        element.acceptChildrenVoid(this)
        if (hasSuspendableChildren) {
            suspendableNodes += element
        }
        hasSuspendableChildren = hasSuspendableChildren or current
    }

    override fun visitSuspensionPoint(expression: IrSuspensionPoint) {
        expression.acceptChildrenVoid(this)
        suspendableNodes += expression
        hasSuspendableChildren = true
    }
}

data class LoopBounds(val headState: SuspendState, val exitState: SuspendState)

class IrDispatchPoint(val target: SuspendState) : IrExpressionBase(UNDEFINED_OFFSET, UNDEFINED_OFFSET, target.entryBlock.type) {
    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D) = visitor.visitExpression(this, data)

    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {}

    override fun <D> transformChildren(transformer: IrElementTransformer<D>, data: D) {}
}

class SuspendState(type: KotlinType) {
    val entryBlock = JsIrBuilder.buildBlock(type)
    val successors = mutableSetOf<SuspendState>()
    var id = -1
}

fun collectSuspendableNodes(body: IrBlock): Set<IrElement> {
    val suspendableNodes = mutableSetOf<IrElement>()

    // 1st: mark suspendable loops and trys
    body.acceptVoid(SuspendableNodesCollector(suspendableNodes))
    // 2nd: mark inner terminators
    body.acceptVoid(SuspendedTerminatorsCollector(suspendableNodes))

    return suspendableNodes
}

class SuspendedTerminatorsCollector(suspendableNodes: MutableSet<IrElement>) : SuspendableNodesCollector(suspendableNodes) {

    override fun visitBreakContinue(jump: IrBreakContinue) {
        if (jump.loop in suspendableNodes) {
            suspendableNodes.add(jump)
            hasSuspendableChildren = true
        }
    }

    private val tryStack = mutableListOf<IrTry>()

    override fun visitTry(aTry: IrTry) {
        tryStack.push(aTry)

        super.visitTry(aTry)

        tryStack.pop()
    }

    override fun visitReturn(expression: IrReturn) {
        val enclosingTry = tryStack.peek()
        if (tryStack.isNotEmpty() && tryStack.peek() in suspendableNodes) {
            suspendableNodes.add(expression)
            hasSuspendableChildren = true
        }
        return super.visitReturn(expression)
    }

    override fun visitThrow(expression: IrThrow) {
        if (tryStack.isNotEmpty() && tryStack.peek() in suspendableNodes) {
            suspendableNodes.add(expression)
            hasSuspendableChildren = true
        }
        return super.visitThrow(expression)
    }
}


class StateMachineBuilder(
    private val suspendableNodes: Set<IrElement>,
    val context: JsIrBackendContext,
    val function: IrFunctionSymbol,
    val rootLoop: IrLoop,
    val suspendResult: () -> IrExpression,
    val resultAccessor: (v: IrExpression?) -> IrExpression,
    val exceptionAccessor: (v: IrExpression?) -> IrExpression,
    val exceptionStateAccessor: (v: IrExpression?) -> IrExpression,
    val suspendCallProcessor: (IrExpression) -> IrExpression
) : IrElementVisitorVoid {
    override fun visitElement(element: IrElement) {
        if (element in suspendableNodes) {
            element.acceptChildrenVoid(this)
        } else {
            addStatement(element as IrStatement)
        }

    }

    val loopMap = mutableMapOf<IrLoop, LoopBounds>()
    private val unit = context.builtIns.unitType
    private val nothing = context.builtIns.nothingType
    private val booleanNotSymbol = context.irBuiltIns.booleanNotSymbol

    val entryState = SuspendState(unit)
    val exceptionTrap = buildExceptionTrap()
    val globalCatchState = SuspendState(unit)
    // TODO: fix it to dynamic
    val globalExceptionSymbol = JsSymbolBuilder.buildTempVar(function, context.builtIns.throwable.defaultType)
    var currentState = entryState
    var currentBlock = entryState.entryBlock
    val states = mutableSetOf<SuspendState>()

    var tryId = 0
    val tryStack = mutableListOf(TryState(tryId++, entryState, globalCatchState, null, SuspendState(unit)))
    val exceptionStates = mutableListOf<TryState>()
    val exceptionTransitions = mutableSetOf(Pair(0, buildExceptionTrapDispatch(exceptionTrap)))

    private fun buildExceptionTrapDispatch(exceptionTrap: SuspendState): IrBlock {
        val block = JsIrBuilder.buildBlock(unit)
        return block
    }

    private fun buildExceptionTransition(target: SuspendState): IrBlock {
        val block = JsIrBuilder.buildBlock(unit)
        block.statements += setPendingException(JsIrBuilder.buildGetValue(globalExceptionSymbol))
        doDispatchImpl(target, block, false)
        return block
    }

    private fun buildExceptionTrap(): SuspendState {
        val state = SuspendState(unit)
        state.entryBlock.statements += JsIrBuilder.buildThrow(nothing, readPendingException())
        return state
    }

    private fun newState() {
        val newState = SuspendState(unit)
        doDispatch(newState)
        updateState(newState)
    }

    private fun updateState(newState: SuspendState) {
        currentState = newState
        currentBlock = newState.entryBlock
    }

    private fun lastExpression() = currentBlock.statements.last() as IrExpression

    private fun IrBlock.addStatement(statement: IrStatement) {
        statements.add(statement)
    }

    private fun setPendingException(expr: IrExpression) = writePendingException(expr)

    private fun addStatement(statement: IrStatement) = currentBlock.addStatement(statement)

    private fun doDispatch(target: SuspendState, andContinue: Boolean = true) = doDispatchImpl(target, currentBlock, andContinue)

    private fun doDispatchImpl(target: SuspendState, block: IrBlock, andContinue: Boolean) {
        val irDispatch = IrDispatchPoint(target)
        currentState.successors.add(target)
        block.addStatement(irDispatch)
        if (andContinue) doContinue(block)
    }

    private fun doContinue(block: IrBlock = currentBlock) {
        block.addStatement(JsIrBuilder.buildContinue(nothing, rootLoop))
    }

    private fun transformLastExpression(transformer: (IrExpression) -> IrStatement) {
        val expression = lastExpression()
        val newStatement = transformer(expression)
        currentBlock.statements[currentBlock.statements.size - 1] = newStatement
    }

    private fun buildDispatchBlock(target: SuspendState) = JsIrBuilder.buildBlock(unit).also { doDispatchImpl(target, it, true) }

    override fun visitWhileLoop(loop: IrWhileLoop) {

        if (loop !in suspendableNodes) return addStatement(loop)

        val condition = loop.condition
        val body = loop.body

        newState()

        val loopHeadState = currentState
        val loopExitState = SuspendState(unit)

        loopMap.put(loop, LoopBounds(loopHeadState, loopExitState))

        condition.acceptVoid(this)

        transformLastExpression {
            val exitCond = JsIrBuilder.buildCall(booleanNotSymbol).apply { putValueArgument(0, it) }
            val irBreak = buildDispatchBlock(loopExitState)
            JsIrBuilder.buildIfElse(unit, exitCond, irBreak)
        }

        body?.acceptVoid(this)

        loopMap.remove(loop)

        doDispatch(loopHeadState)
        updateState(loopExitState)
    }

    override fun visitDoWhileLoop(loop: IrDoWhileLoop) {

        if (loop !in suspendableNodes) return addStatement(loop)

        val condition = loop.condition
        val body = loop.body

        newState()

        val loopHeadState = currentState
        val loopExitState = SuspendState(unit)

        loopMap.put(loop, LoopBounds(loopHeadState, loopExitState))

        body?.acceptVoid(this)

        condition.acceptVoid(this)

        transformLastExpression {
            val irContinue = buildDispatchBlock(loopHeadState)
            JsIrBuilder.buildIfElse(unit, it, irContinue)
        }

        loopMap.remove(loop)

        updateState(loopExitState)
    }


    private fun implicitCast(value: IrExpression, toType: KotlinType) =
        JsIrBuilder.buildTypeOperator(
            toType, IrTypeOperator.IMPLICIT_CAST, value, toType,
            context.symbolTable.referenceClassifier(toType.constructor.declarationDescriptor!!)
        )

    override fun visitSuspensionPoint(expression: IrSuspensionPoint) {

        val continueState = SuspendState(unit)
        doDispatch(continueState, false)
        addStatement(suspendCallProcessor(expression.suspendableExpression))
        doContinue()

        updateState(continueState)

        val type = expression.type
        val implicitCast = implicitCast(suspendResult(), type)
        addStatement(implicitCast)
    }

    private fun processStatements(statements: Collection<IrStatement>) {
        for (stmt in statements) {
            if (stmt in suspendableNodes) {
                stmt.acceptVoid(this)
            } else {
                addStatement(stmt)
            }
        }
    }

//    override fun visitContainerExpression(expression: IrContainerExpression) {
//
//        if (expression !in suspendableNodes) {
//            currentState.addStatement(expression)
//            return
//        }
//
//        processStatements(expression.statements)
//    }
//
//    override fun visitBlockBody(body: IrBlockBody) {
//        processStatements(body.statements)
//    }

    override fun visitBreak(jump: IrBreak) {
        val exitState = loopMap[jump.loop]!!.exitState
        doDispatch(exitState)
    }

    override fun visitContinue(jump: IrContinue) {
        val headState = loopMap[jump.loop]!!.headState
        doDispatch(headState)
    }

    override fun visitWhen(expression: IrWhen) {

        if (expression !in suspendableNodes) return addStatement(expression)

        val exitState = SuspendState(expression.type)

        for (branch in expression.branches) {

            //  TODO: optimize for else-branch
            branch.condition.acceptVoid(this)
            val branchBlock = JsIrBuilder.buildBlock(branch.result.type)
            val elseBlock = JsIrBuilder.buildBlock(expression.type)

            transformLastExpression {
                JsIrBuilder.buildIfElse(unit, it, branchBlock, elseBlock)
            }

            val ifState = currentState

            currentBlock = branchBlock
            branch.result.acceptVoid(this)

            if (currentState != ifState) {
                doDispatch(exitState)
                currentState = ifState
            }
            currentBlock = elseBlock
        }

        doDispatch(exitState)
        updateState(exitState)
    }

    override fun visitSetVariable(expression: IrSetVariable) {
        assert(expression in suspendableNodes)
        expression.acceptChildrenVoid(this)
        transformLastExpression { expression.apply { value = it } }
    }

    override fun visitVariable(declaration: IrVariable) {
        assert(declaration in suspendableNodes)
        declaration.acceptChildrenVoid(this)
        transformLastExpression { declaration.apply { initializer = it } }
    }

    override fun visitGetField(expression: IrGetField) {
        assert(expression in suspendableNodes)
        expression.acceptChildrenVoid(this)
        transformLastExpression { expression.apply { receiver = it } }
    }

    override fun visitGetClass(expression: IrGetClass) {
        assert(expression in suspendableNodes)
        expression.acceptChildrenVoid(this)
        transformLastExpression { expression.apply { argument = it } }
    }

    private fun transformArguments(arguments: Array<IrExpression?>): Array<IrExpression?> {

        var suspendableCount = arguments.fold(0) { r, n -> if (n in suspendableNodes) r + 1 else r }

        val newArguments = arrayOfNulls<IrExpression>(arguments.size)

        for ((i, arg) in arguments.withIndex()) {
            newArguments[i] = if (arg != null && suspendableCount > 0) {
                if (arg in suspendableNodes) suspendableCount--
                arg.acceptVoid(this)
                val tmp = tempVar(arg.type)
                transformLastExpression { JsIrBuilder.buildVar(tmp, it) }
                JsIrBuilder.buildGetValue(tmp)
            } else arg
        }

        return newArguments
    }

    override fun visitMemberAccess(expression: IrMemberAccessExpression) {

        if (expression !in suspendableNodes) return addStatement(expression)

        val arguments = arrayOfNulls<IrExpression>(expression.valueArgumentsCount + 2)
        arguments[0] = expression.dispatchReceiver
        arguments[1] = expression.extensionReceiver

        for (i in 0 until expression.valueArgumentsCount) {
            arguments[i + 2] = expression.getValueArgument(i)
        }

        val newArguments = transformArguments(arguments)

        expression.dispatchReceiver = newArguments[0]
        expression.extensionReceiver = newArguments[1]
        for (i in 0 until expression.valueArgumentsCount) {
            expression.putValueArgument(i, newArguments[i + 2])
        }

        addStatement(expression)
    }

    override fun visitSetField(expression: IrSetField) {
        if (expression !in suspendableNodes) return addStatement(expression)

        val newArguments = transformArguments(arrayOf(expression.receiver, expression.value))

        val receiver = newArguments[0]
        val value = newArguments[1] as IrExpression

        addStatement(expression.run { IrSetFieldImpl(startOffset, endOffset, symbol, receiver, value, origin, superQualifierSymbol) })
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation) {
        assert(expression in suspendableNodes)

        val arguments = arrayOfNulls<IrExpression>(expression.arguments.size)

        expression.arguments.forEachIndexed { i, a -> arguments[i] = a }

        val newArguments = transformArguments(arguments)

        addStatement(expression.run { IrStringConcatenationImpl(startOffset, endOffset, type, newArguments.map { it!! }) })
    }

    override fun visitReturn(expression: IrReturn) {
        val finallyState = tryStack.firstOrNull { it.finallyState != null }?.finallyState

        expression.acceptChildrenVoid(this)

        if (finallyState != null) {
            transformLastExpression { writePendingResult(it) }
            doDispatch(finallyState.fromReturn)
        } else {
            transformLastExpression { expression.apply { value = it } }
        }
    }

    override fun visitThrow(expression: IrThrow) {
        expression.acceptChildrenVoid(this)
        transformLastExpression { expression.apply { value = it } }
    }


    private fun readPendingResult() = resultAccessor(null)
    private fun writePendingResult(value: IrExpression) = resultAccessor(value)

    private fun readPendingException() = exceptionAccessor(null)
    private fun writePendingException(value: IrExpression) = exceptionAccessor(value)



    val normanID = JsIrBuilder.buildInt(context.builtIns.intType, 0)
    val returnID = JsIrBuilder.buildInt(context.builtIns.intType, 1)
    val throwID = JsIrBuilder.buildInt(context.builtIns.intType, 2)

    override fun visitTry(aTry: IrTry) {
        val tryState = buildTryState(aTry)
        val outState = tryStack.peek()!!

        tryStack.push(tryState)

        doDispatch(tryState.tryState)
        updateState(tryState.tryState)

        val finallyStateVarSymbol = tempVar(context.builtIns.intType)
        if (aTry.finallyExpression != null) {
            addStatement(JsIrBuilder.buildVar(finallyStateVarSymbol, normanID))
        }

        setExceptionState(tryState.id)

        aTry.tryResult.acceptVoid(this)

        val exitState = SuspendState(unit)

        tryState.finallyState?.also {
            doDispatch(it.normal)
        } ?: setExceptionState(outState.id)

        states += tryState.catchState
        tryState.tryState.successors += tryState.catchState
        updateState(tryState.catchState)

        val continueExId = tryState.finallyState?.finallyExId ?: outState.id
        setExceptionState(continueExId)

        exceptionTransitions.add(Pair(tryState.id, buildExceptionTransition(tryState.catchState)))

        val ex = readPendingException()
        for (catch in aTry.catches) {
            val type = catch.catchParameter.type
            val check = buildIsCheck(ex, type)
            val branchBlock = JsIrBuilder.buildBlock(catch.result.type)
            val elseBlock = JsIrBuilder.buildBlock(catch.result.type)
            val irIf = JsIrBuilder.buildIfElse(catch.result.type, check, branchBlock, elseBlock)
            val ifBlock = currentBlock
            currentBlock = branchBlock
            val irVar = catch.catchParameter.apply { initializer = implicitCast(ex, type) }
            addStatement(irVar)
            catch.result.acceptVoid(this)
            tryState.finallyState?.also { doDispatch(it.normal) }
            currentBlock = ifBlock
            addStatement(irIf)
            currentBlock = elseBlock
        }

        addStatement(JsIrBuilder.buildThrow(nothing, ex))

        tryStack.pop()

        val finallyState = tryState.finallyState
        if (finallyState != null) {
            updateState(finallyState.fromThrow)
            tryState.tryState.successors += finallyState.fromThrow
            addStatement(JsIrBuilder.buildSetVariable(finallyStateVarSymbol, throwID))
            doDispatch(finallyState.normal)

            updateState(finallyState.fromReturn)
            tryState.tryState.successors += finallyState.fromReturn
            addStatement(JsIrBuilder.buildSetVariable(finallyStateVarSymbol, returnID))
            doDispatch(finallyState.normal)

            updateState(finallyState.normal)
            tryState.tryState.successors += finallyState.normal
            setExceptionState(outState.id)
            aTry.finallyExpression?.acceptVoid(this)
            buildFinallyDispatch(exitState, finallyStateVarSymbol)


            exceptionTransitions.add(Pair(finallyState.finallyExId, buildExceptionTransition(finallyState.fromThrow)))
        }

        updateState(exitState)
    }

    private fun buildFinallyDispatch(exitState: SuspendState, stateVar: IrVariableSymbol) {
        val value = JsIrBuilder.buildGetValue(stateVar)
        val normaDispatchBlock = buildDispatchBlock(exitState)
        val elseNormalBlock = JsIrBuilder.buildBlock(unit)
        val checkNormal = JsIrBuilder.buildCall(context.irBuiltIns.eqeqSymbol).apply {
            putValueArgument(0, value)
            putValueArgument(1, normanID)
        }
        val irIfNormal = JsIrBuilder.buildIfElse(unit, checkNormal, normaDispatchBlock, elseNormalBlock)
        addStatement(irIfNormal)
        currentBlock = elseNormalBlock
        val checkReturn = JsIrBuilder.buildCall(context.irBuiltIns.eqeqSymbol).apply {
            putValueArgument(0, value)
            putValueArgument(1, returnID)
        }

        val enclosingState = tryStack.firstOrNull { it.finallyState != null }
        val returnStatement = if (enclosingState == null) {
            JsIrBuilder.buildReturn(function, readPendingResult())
        } else {
            buildDispatchBlock(enclosingState.finallyState!!.fromReturn)
        }

        val irThrowStatement = JsIrBuilder.buildThrow(nothing, readPendingException())
        val irIfReturn = JsIrBuilder.buildIfElse(unit, checkReturn, returnStatement, irThrowStatement)

        elseNormalBlock.statements += irIfReturn

        updateState(exitState)
    }

    private fun setExceptionState(id: Int) {
        addStatement(exceptionStateAccessor(JsIrBuilder.buildInt(context.builtIns.intType, id)))
    }

    private fun buildTryState(aTry: IrTry) =
        TryState(
            tryId++,
            SuspendState(unit),
            SuspendState(unit),
            aTry.finallyExpression?.run { FinallyTargets(tryId++, SuspendState(unit), SuspendState(unit), SuspendState(unit)) },
            SuspendState(unit)
        )


    private fun buildIsCheck(value: IrExpression, toType: KotlinType) =
        JsIrBuilder.buildTypeOperator(
            context.irBuiltIns.bool,
            IrTypeOperator.INSTANCEOF,
            value,
            toType,
            context.symbolTable.referenceClassifier(toType.constructor.declarationDescriptor!!)
        )

    private fun tempVar(type: KotlinType) = JsSymbolBuilder.buildTempVar(function, type)
}

data class FinallyTargets(val finallyExId: Int, val normal: SuspendState, val fromReturn: SuspendState, val fromThrow: SuspendState)

class TryState(
    val id: Int,
    val tryState: SuspendState,
    val catchState: SuspendState,
    val finallyState: FinallyTargets?,
    val exitState: SuspendState
)


class DispatchPointTransformer(val action: (SuspendState) -> IrExpression) : IrElementTransformerVoid() {
    override fun visitExpression(expression: IrExpression): IrExpression {
        val dispatchPoint = expression as? IrDispatchPoint ?: return super.visitExpression(expression)
        return action(dispatchPoint.target)
    }
}