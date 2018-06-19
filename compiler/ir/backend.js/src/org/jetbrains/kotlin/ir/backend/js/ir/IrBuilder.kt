/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.ir

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrPropertyImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrValueParameterImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.symbols.impl.IrTypeParameterSymbolImpl
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.ir.util.createParameterDeclarations
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.resolve.OverridingStrategy
import org.jetbrains.kotlin.resolve.OverridingUtil
import org.jetbrains.kotlin.types.KotlinType
import java.lang.reflect.Proxy

object JsIrBuilder {

    object SYNTHESIZED_STATEMENT : IrStatementOriginImpl("SYNTHESIZED_STATEMENT")
    object SYNTHESIZED_DECLARATION : IrDeclarationOriginImpl("SYNTHESIZED_DECLARATION")

    fun buildCall(target: IrFunctionSymbol, type: KotlinType? = null, typeArguments: Map<TypeParameterDescriptor, KotlinType>? = null) =
        IrCallImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            type ?: target.descriptor.returnType!!,
            target,
            target.descriptor,
            typeArguments,
            SYNTHESIZED_STATEMENT
        )

    fun buildReturn(targetSymbol: IrFunctionSymbol, value: IrExpression) =
        IrReturnImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, targetSymbol, value)

    fun buildThrow(type: KotlinType, value: IrExpression) = IrThrowImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, value)

    fun buildValueParameter(symbol: IrValueParameterSymbol) =
        IrValueParameterImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, SYNTHESIZED_DECLARATION, symbol)

    fun buildFunction(symbol: IrSimpleFunctionSymbol) = IrFunctionImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, SYNTHESIZED_DECLARATION, symbol)

    fun buildGetObjectValue(type: KotlinType, classSymbol: IrClassSymbol) =
        IrGetObjectValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, classSymbol)

    fun buildGetClass(expression: IrExpression, type: KotlinType) = IrGetClassImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, expression)

    fun buildGetValue(symbol: IrValueSymbol) = IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, symbol, SYNTHESIZED_STATEMENT)
    fun buildSetVariable(symbol: IrVariableSymbol, value: IrExpression) =
        IrSetVariableImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, symbol, value, SYNTHESIZED_STATEMENT)

    fun buildGetField(symbol: IrFieldSymbol, receiver: IrExpression?, superQualifierSymbol: IrClassSymbol? = null) =
        IrGetFieldImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, symbol, receiver, SYNTHESIZED_STATEMENT, superQualifierSymbol)

    fun buildSetField(symbol: IrFieldSymbol, receiver: IrExpression?, value: IrExpression, superQualifierSymbol: IrClassSymbol? = null) =
        IrSetFieldImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, symbol, receiver, value, SYNTHESIZED_STATEMENT, superQualifierSymbol)

    fun buildBlockBody(statements: List<IrStatement>) = IrBlockBodyImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, statements)

    fun buildBlock(type: KotlinType) = IrBlockImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, SYNTHESIZED_STATEMENT)
    fun buildBlock(type: KotlinType, statements: List<IrStatement>) =
        IrBlockImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, SYNTHESIZED_STATEMENT, statements)

    fun buildComposite(type: KotlinType, statements: List<IrStatement> = emptyList()) =
        IrCompositeImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, SYNTHESIZED_STATEMENT, statements)

    fun buildFunctionReference(type: KotlinType, symbol: IrFunctionSymbol) =
        IrFunctionReferenceImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, symbol, symbol.descriptor)

    fun buildVar(symbol: IrVariableSymbol, initializer: IrExpression? = null) =
        IrVariableImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, SYNTHESIZED_DECLARATION, symbol).apply { this.initializer = initializer }

    fun buildBreak(type: KotlinType, loop: IrLoop) = IrBreakImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, loop)
    fun buildContinue(type: KotlinType, loop: IrLoop) = IrContinueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, loop)

    fun buildIfElse(type: KotlinType, cond: IrExpression, thenBranch: IrExpression, elseBranch: IrExpression? = null) = IrIfThenElseImpl(
        UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, cond, thenBranch, elseBranch, SYNTHESIZED_STATEMENT
    )

    fun buildWhen(type: KotlinType, branches: List<IrBranch>) =
        IrWhenImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, SYNTHESIZED_STATEMENT, branches)

    fun buildTypeOperator(
        type: KotlinType,
        operator: IrTypeOperator,
        argument: IrExpression,
        toType: KotlinType,
        symbol: IrClassifierSymbol
    ) =
        IrTypeOperatorCallImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, operator, toType, argument, symbol)

    fun buildNull(type: KotlinType) = IrConstImpl.constNull(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type)
    fun buildBoolean(type: KotlinType, v: Boolean) = IrConstImpl.boolean(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, v)
    fun buildInt(type: KotlinType, v: Int) = IrConstImpl.int(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, v)
    fun buildString(type: KotlinType, s: String) = IrConstImpl.string(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, s)
}


object SetDeclarationsParentVisitor : IrElementVisitor<Unit, IrDeclarationParent> {
    override fun visitElement(element: IrElement, data: IrDeclarationParent) {
        if (element !is IrDeclarationParent) {
            element.acceptChildren(this, data)
        }
    }

    override fun visitDeclaration(declaration: IrDeclaration, data: IrDeclarationParent) {
        declaration.parent = data
        super.visitDeclaration(declaration, data)
    }
}

fun IrDeclarationContainer.addChildren(declarations: List<IrDeclaration>) {
    declarations.forEach { this.addChild(it) }
}

fun IrDeclarationContainer.addChild(declaration: IrDeclaration) {
    this.declarations += declaration
    declaration.accept(SetDeclarationsParentVisitor, this)
}

fun IrTypeParameter.setSupers(symbolTable: SymbolTable) {
    assert(this.superClassifiers.isEmpty())
    this.descriptor.upperBounds.mapNotNullTo(this.superClassifiers) {
        it.constructor.declarationDescriptor?.let {
            if (it is TypeParameterDescriptor) {
                IrTypeParameterSymbolImpl(it) // Workaround for deserialized inline functions
            } else {
                symbolTable.referenceClassifier(it)
            }
        }
    }
}

fun IrClass.simpleFunctions(): List<IrSimpleFunction> = this.declarations.flatMap {
    when (it) {
        is IrSimpleFunction -> listOf(it)
        is IrProperty -> listOfNotNull(it.getter as IrSimpleFunction?, it.setter as IrSimpleFunction?)
        else -> emptyList()
    }
}

fun IrClass.setSuperSymbols(supers: List<IrClass>) {
    val s1  =this.superDescriptors().toSet()
    val s2 = supers.map { it.descriptor }.toSet()
    assert(s1 == s2)
    assert(this.superClasses.isEmpty())
    supers.mapTo(this.superClasses) { it.symbol }

    val superMembers = supers.flatMap {
        it.simpleFunctions()
    }.associateBy { it.descriptor }

    this.simpleFunctions().forEach {
        assert(it.overriddenSymbols.isEmpty())

        it.descriptor.overriddenDescriptors.mapTo(it.overriddenSymbols) {
            val superMember = superMembers[it.original] ?: error(it.original)
            superMember.symbol
        }
    }
}

fun IrSimpleFunction.setOverrides(symbolTable: SymbolTable) {
    assert(this.overriddenSymbols.isEmpty())

    this.descriptor.overriddenDescriptors.mapTo(this.overriddenSymbols) {
        symbolTable.referenceSimpleFunction(it.original)
    }

    this.typeParameters.forEach { it.setSupers(symbolTable) }
}


private fun IrClass.superDescriptors() =
    this.descriptor.typeConstructor.supertypes.map { it.constructor.declarationDescriptor as ClassDescriptor }

fun IrClass.setSuperSymbols(symbolTable: SymbolTable) {
    assert(this.superClasses.isEmpty())
    this.superDescriptors().mapTo(this.superClasses) { symbolTable.referenceClass(it) }
    this.simpleFunctions().forEach {
        it.setOverrides(symbolTable)
    }
    this.typeParameters.forEach {
        it.setSupers(symbolTable)
    }
}

private fun createFakeOverride(descriptor: CallableMemberDescriptor, startOffset: Int, endOffset: Int): IrDeclaration {

    fun FunctionDescriptor.createFunction(): IrFunction = IrFunctionImpl(
        startOffset, endOffset,
        IrDeclarationOrigin.FAKE_OVERRIDE, this
    ).apply {
        createParameterDeclarations()
    }

    return when (descriptor) {
        is FunctionDescriptor -> descriptor.createFunction()
        is PropertyDescriptor ->
            IrPropertyImpl(startOffset, endOffset, IrDeclarationOrigin.FAKE_OVERRIDE, descriptor).apply {
                // TODO: add field if getter is missing?
                getter = descriptor.getter?.createFunction() as IrSimpleFunction?
                setter = descriptor.setter?.createFunction() as IrSimpleFunction?
            }
        else -> TODO(descriptor.toString())
    }
}


fun IrClass.setSuperSymbolsAndAddFakeOverrides(supers: List<IrClass>) {
    val overriddenSuperMembers = this.declarations.map { it.descriptor }
        .filterIsInstance<CallableMemberDescriptor>().flatMap { it.overriddenDescriptors.map { it.original } }

    val unoverriddenSuperMembers = supers.flatMap {
        it.declarations.mapNotNull {
            when (it) {
                is IrSimpleFunction -> it.descriptor
                is IrProperty -> it.descriptor
                else -> null
            }
        }
    } - overriddenSuperMembers

    val irClass = this

    val overridingStrategy = object : OverridingStrategy() {
        override fun addFakeOverride(fakeOverride: CallableMemberDescriptor) {
            irClass.addChild(createFakeOverride(fakeOverride, startOffset, endOffset))
        }

        override fun inheritanceConflict(first: CallableMemberDescriptor, second: CallableMemberDescriptor) {
            error("inheritance conflict in synthesized class ${irClass.descriptor}:\n  $first\n  $second")
        }

        override fun overrideConflict(fromSuper: CallableMemberDescriptor, fromCurrent: CallableMemberDescriptor) {
            error("override conflict in synthesized class ${irClass.descriptor}:\n  $fromSuper\n  $fromCurrent")
        }
    }

    unoverriddenSuperMembers.groupBy { it.name }.forEach { (name, members) ->
        OverridingUtil.generateOverridesInFunctionGroup(
            name,
            members,
            emptyList(),
            this.descriptor,
            overridingStrategy
        )
    }

    this.setSuperSymbols(supers)
}

inline fun <reified T> stub(name: String): T {
    return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) {
            _ /* proxy */, method, _ /* methodArgs */ ->
        if (method.name == "toString" && method.parameterCount == 0) {
            "${T::class.simpleName} stub for $name"
        } else {
            error("${T::class.simpleName}.${method.name} is not supported for $name")
        }
    } as T
}