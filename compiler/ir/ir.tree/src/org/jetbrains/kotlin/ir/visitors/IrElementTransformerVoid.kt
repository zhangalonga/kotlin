/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.ir.visitors

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*

abstract class IrElementTransformerVoid : IrElementTransformer<Nothing?> {
    protected fun <T : IrElement> T.transformChildren(): T = apply { transformChildrenVoid() }

    open fun visitElement(element: IrElement): IrElement = element.transformChildren()
    override final fun visitElement(element: IrElement, data: Nothing?): IrElement = visitElement(element)

    open fun visitModuleFragment(declaration: IrModuleFragment): IrModuleFragment = declaration.transformChildren()
    override final fun visitModuleFragment(declaration: IrModuleFragment, data: Nothing?): IrModuleFragment =
        visitModuleFragment(declaration)

    open fun visitPackageFragment(declaration: IrPackageFragment): IrPackageFragment = declaration.transformChildren()
    override fun visitPackageFragment(declaration: IrPackageFragment, data: Nothing?): IrElement = visitPackageFragment(declaration)

    open fun visitFile(declaration: IrFile): IrFile = visitPackageFragment(declaration) as IrFile
    override final fun visitFile(declaration: IrFile, data: Nothing?): IrFile = visitFile(declaration)

    open fun visitExternalPackageFragment(declaration: IrExternalPackageFragment): IrExternalPackageFragment =
        visitPackageFragment(declaration) as IrExternalPackageFragment

    override fun visitExternalPackageFragment(declaration: IrExternalPackageFragment, data: Nothing?): IrExternalPackageFragment =
        visitExternalPackageFragment(declaration)

    open fun visitDeclaration(declaration: IrDeclaration): IrStatement = declaration.transformChildren()
    override final fun visitDeclaration(declaration: IrDeclaration, data: Nothing?): IrStatement = visitDeclaration(declaration)

    open fun visitClass(declaration: IrClass): IrStatement = visitDeclaration(declaration)
    override final fun visitClass(declaration: IrClass, data: Nothing?): IrStatement = visitClass(declaration)

    open fun visitTypeAlias(declaration: IrTypeAlias): IrStatement = visitDeclaration(declaration)
    override final fun visitTypeAlias(declaration: IrTypeAlias, data: Nothing?): IrStatement = visitTypeAlias(declaration)

    open fun visitFunction(declaration: IrFunction): IrStatement = visitDeclaration(declaration)
    override final fun visitFunction(declaration: IrFunction, data: Nothing?): IrStatement = visitFunction(declaration)

    open fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement = visitFunction(declaration)
    override final fun visitSimpleFunction(declaration: IrSimpleFunction, data: Nothing?): IrStatement = visitSimpleFunction(declaration)

    open fun visitConstructor(declaration: IrConstructor): IrStatement = visitFunction(declaration)
    override final fun visitConstructor(declaration: IrConstructor, data: Nothing?): IrStatement = visitConstructor(declaration)

    open fun visitProperty(declaration: IrProperty): IrStatement = visitDeclaration(declaration)
    override final fun visitProperty(declaration: IrProperty, data: Nothing?): IrStatement = visitProperty(declaration)

    open fun visitField(declaration: IrField): IrStatement = visitDeclaration(declaration)
    override final fun visitField(declaration: IrField, data: Nothing?): IrStatement = visitField(declaration)

    open fun visitLocalDelegatedProperty(declaration: IrLocalDelegatedProperty): IrStatement = visitDeclaration(declaration)
    override final fun visitLocalDelegatedProperty(declaration: IrLocalDelegatedProperty, data: Nothing?): IrStatement =
        visitLocalDelegatedProperty(declaration)

    open fun visitEnumEntry(declaration: IrEnumEntry): IrStatement = visitDeclaration(declaration)
    override final fun visitEnumEntry(declaration: IrEnumEntry, data: Nothing?): IrStatement = visitEnumEntry(declaration)

    open fun visitAnonymousInitializer(declaration: IrAnonymousInitializer): IrStatement = visitDeclaration(declaration)
    override final fun visitAnonymousInitializer(declaration: IrAnonymousInitializer, data: Nothing?): IrStatement =
        visitAnonymousInitializer(declaration)

    open fun visitTypeParameter(declaration: IrTypeParameter): IrStatement = visitDeclaration(declaration)
    override final fun visitTypeParameter(declaration: IrTypeParameter, data: Nothing?): IrStatement = visitTypeParameter(declaration)

    open fun visitValueParameter(declaration: IrValueParameter): IrStatement = visitDeclaration(declaration)
    override final fun visitValueParameter(declaration: IrValueParameter, data: Nothing?): IrStatement = visitValueParameter(declaration)

    open fun visitVariable(declaration: IrVariable): IrStatement = visitDeclaration(declaration)
    override final fun visitVariable(declaration: IrVariable, data: Nothing?): IrStatement = visitVariable(declaration)

    open fun visitBody(body: IrBody): IrBody = body.transformChildren()
    override final fun visitBody(body: IrBody, data: Nothing?): IrBody = visitBody(body)

    open fun visitExpressionBody(body: IrExpressionBody): IrBody = visitBody(body)
    override final fun visitExpressionBody(body: IrExpressionBody, data: Nothing?): IrBody = visitExpressionBody(body)

    open fun visitBlockBody(body: IrBlockBody): IrBody = visitBody(body)
    override final fun visitBlockBody(body: IrBlockBody, data: Nothing?): IrBody = visitBlockBody(body)

    open fun visitSyntheticBody(body: IrSyntheticBody): IrBody = visitBody(body)
    override final fun visitSyntheticBody(body: IrSyntheticBody, data: Nothing?): IrBody = visitSyntheticBody(body)

    open fun visitExpression(expression: IrExpression): IrExpression = expression.transformChildren()
    override final fun visitExpression(expression: IrExpression, data: Nothing?): IrExpression = visitExpression(expression)

    open fun <T> visitConst(expression: IrConst<T>): IrExpression = visitExpression(expression)
    override final fun <T> visitConst(expression: IrConst<T>, data: Nothing?): IrExpression = visitConst(expression)

    open fun visitVararg(expression: IrVararg): IrExpression = visitExpression(expression)
    override final fun visitVararg(expression: IrVararg, data: Nothing?): IrExpression = visitVararg(expression)

    open fun visitSpreadElement(spread: IrSpreadElement): IrSpreadElement = spread.transformChildren()
    override final fun visitSpreadElement(spread: IrSpreadElement, data: Nothing?): IrSpreadElement = visitSpreadElement(spread)

    open fun visitContainerExpression(expression: IrContainerExpression): IrExpression = visitExpression(expression)
    override final fun visitContainerExpression(expression: IrContainerExpression, data: Nothing?): IrExpression = visitContainerExpression(expression)

    open fun visitBlock(expression: IrBlock): IrExpression = visitContainerExpression(expression)
    override final fun visitBlock(expression: IrBlock, data: Nothing?): IrExpression = visitBlock(expression)

    open fun visitComposite(expression: IrComposite): IrExpression = visitContainerExpression(expression)
    override final fun visitComposite(expression: IrComposite, data: Nothing?): IrExpression = visitComposite(expression)

    open fun visitStringConcatenation(expression: IrStringConcatenation): IrExpression = visitExpression(expression)
    override final fun visitStringConcatenation(expression: IrStringConcatenation, data: Nothing?): IrExpression = visitStringConcatenation(expression)

    open fun visitDeclarationReference(expression: IrDeclarationReference): IrExpression = visitExpression(expression)
    override final fun visitDeclarationReference(expression: IrDeclarationReference, data: Nothing?): IrExpression = visitDeclarationReference(expression)

    open fun visitSingletonReference(expression: IrGetSingletonValue): IrExpression = visitDeclarationReference(expression)
    override final fun visitSingletonReference(expression: IrGetSingletonValue, data: Nothing?): IrExpression = visitSingletonReference(expression)

    open fun visitGetObjectValue(expression: IrGetObjectValue): IrExpression = visitSingletonReference(expression)
    override final fun visitGetObjectValue(expression: IrGetObjectValue, data: Nothing?): IrExpression = visitGetObjectValue(expression)

    open fun visitGetEnumValue(expression: IrGetEnumValue): IrExpression = visitSingletonReference(expression)
    override final fun visitGetEnumValue(expression: IrGetEnumValue, data: Nothing?): IrExpression = visitGetEnumValue(expression)

    open fun visitValueAccess(expression: IrValueAccessExpression): IrExpression = visitDeclarationReference(expression)
    override final fun visitValueAccess(expression: IrValueAccessExpression, data: Nothing?): IrExpression = visitValueAccess(expression)

    open fun visitGetValue(expression: IrGetValue): IrExpression = visitValueAccess(expression)
    override final fun visitGetValue(expression: IrGetValue, data: Nothing?): IrExpression = visitGetValue(expression)

    open fun visitSetVariable(expression: IrSetVariable): IrExpression = visitValueAccess(expression)
    override final fun visitSetVariable(expression: IrSetVariable, data: Nothing?): IrExpression = visitSetVariable(expression)

    open fun visitFieldAccess(expression: IrFieldAccessExpression): IrExpression = visitDeclarationReference(expression)
    override final fun visitFieldAccess(expression: IrFieldAccessExpression, data: Nothing?): IrExpression = visitFieldAccess(expression)

    open fun visitGetField(expression: IrGetField): IrExpression = visitFieldAccess(expression)
    override final fun visitGetField(expression: IrGetField, data: Nothing?): IrExpression = visitGetField(expression)

    open fun visitSetField(expression: IrSetField): IrExpression = visitFieldAccess(expression)
    override final fun visitSetField(expression: IrSetField, data: Nothing?): IrExpression = visitSetField(expression)

    open fun visitMemberAccess(expression: IrMemberAccessExpression): IrExpression = visitExpression(expression)
    override final fun visitMemberAccess(expression: IrMemberAccessExpression, data: Nothing?): IrExpression = visitMemberAccess(expression)

    open fun visitFunctionAccess(expression: IrFunctionAccessExpression): IrExpression = visitMemberAccess(expression)
    override fun visitFunctionAccess(expression: IrFunctionAccessExpression, data: Nothing?): IrExpression = visitFunctionAccess(expression)

    open fun visitCall(expression: IrCall): IrExpression = visitFunctionAccess(expression)
    override final fun visitCall(expression: IrCall, data: Nothing?): IrExpression = visitCall(expression)

    open fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall): IrExpression = visitMemberAccess(expression)
    override final fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall, data: Nothing?): IrExpression =
        visitDelegatingConstructorCall(expression)

    open fun visitEnumConstructorCall(expression: IrEnumConstructorCall): IrExpression = visitMemberAccess(expression)
    override final fun visitEnumConstructorCall(expression: IrEnumConstructorCall, data: Nothing?): IrExpression = visitEnumConstructorCall(expression)

    open fun visitGetClass(expression: IrGetClass): IrExpression = visitExpression(expression)
    override final fun visitGetClass(expression: IrGetClass, data: Nothing?): IrExpression = visitGetClass(expression)

    open fun visitCallableReference(expression: IrCallableReference): IrExpression = visitMemberAccess(expression)
    override final fun visitCallableReference(expression: IrCallableReference, data: Nothing?): IrExpression = visitCallableReference(expression)

    open fun visitFunctionReference(expression: IrFunctionReference): IrExpression = visitCallableReference(expression)
    override final fun visitFunctionReference(expression: IrFunctionReference, data: Nothing?): IrElement =
        visitFunctionReference(expression)

    open fun visitPropertyReference(expression: IrPropertyReference): IrExpression = visitCallableReference(expression)
    override final fun visitPropertyReference(expression: IrPropertyReference, data: Nothing?): IrElement =
        visitPropertyReference(expression)

    open fun visitLocalDelegatedPropertyReference(expression: IrLocalDelegatedPropertyReference): IrExpression = visitCallableReference(expression)
    override final fun visitLocalDelegatedPropertyReference(expression: IrLocalDelegatedPropertyReference, data: Nothing?): IrExpression =
        visitLocalDelegatedPropertyReference(expression)

    open fun visitClassReference(expression: IrClassReference): IrExpression = visitDeclarationReference(expression)
    override final fun visitClassReference(expression: IrClassReference, data: Nothing?): IrExpression = visitClassReference(expression)

    open fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall): IrExpression = visitExpression(expression)
    override final fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall, data: Nothing?): IrExpression =
        visitInstanceInitializerCall(expression)

    open fun visitTypeOperator(expression: IrTypeOperatorCall): IrExpression = visitExpression(expression)
    override final fun visitTypeOperator(expression: IrTypeOperatorCall, data: Nothing?): IrExpression = visitTypeOperator(expression)

    open fun visitWhen(expression: IrWhen): IrExpression = visitExpression(expression)
    override final fun visitWhen(expression: IrWhen, data: Nothing?): IrExpression = visitWhen(expression)

    open fun visitBranch(branch: IrBranch): IrBranch = branch.transformChildren()
    override final fun visitBranch(branch: IrBranch, data: Nothing?): IrBranch = visitBranch(branch)

    open fun visitElseBranch(branch: IrElseBranch): IrElseBranch = branch.transformChildren()
    override final fun visitElseBranch(branch: IrElseBranch, data: Nothing?): IrElseBranch = visitElseBranch(branch)

    open fun visitLoop(loop: IrLoop): IrExpression = visitExpression(loop)
    override final fun visitLoop(loop: IrLoop, data: Nothing?): IrExpression = visitLoop(loop)

    open fun visitWhileLoop(loop: IrWhileLoop): IrExpression = visitLoop(loop)
    override final fun visitWhileLoop(loop: IrWhileLoop, data: Nothing?): IrExpression = visitWhileLoop(loop)

    open fun visitDoWhileLoop(loop: IrDoWhileLoop): IrExpression = visitLoop(loop)
    override final fun visitDoWhileLoop(loop: IrDoWhileLoop, data: Nothing?): IrExpression = visitDoWhileLoop(loop)

    open fun visitTry(aTry: IrTry): IrExpression = visitExpression(aTry)
    override final fun visitTry(aTry: IrTry, data: Nothing?): IrExpression = visitTry(aTry)

    open fun visitCatch(aCatch: IrCatch): IrCatch = aCatch.apply { transformChildrenVoid() }
    override final fun visitCatch(aCatch: IrCatch, data: Nothing?): IrCatch = visitCatch(aCatch)

    open fun visitBreakContinue(jump: IrBreakContinue): IrExpression = visitExpression(jump)
    override final fun visitBreakContinue(jump: IrBreakContinue, data: Nothing?): IrExpression = visitBreakContinue(jump)

    open fun visitBreak(jump: IrBreak): IrExpression = visitBreakContinue(jump)
    override final fun visitBreak(jump: IrBreak, data: Nothing?): IrExpression = visitBreak(jump)

    open fun visitContinue(jump: IrContinue): IrExpression = visitBreakContinue(jump)
    override final fun visitContinue(jump: IrContinue, data: Nothing?): IrExpression = visitContinue(jump)

    open fun visitReturn(expression: IrReturn): IrExpression = visitExpression(expression)
    override final fun visitReturn(expression: IrReturn, data: Nothing?): IrExpression = visitReturn(expression)

    open fun visitThrow(expression: IrThrow): IrExpression = visitExpression(expression)
    override final fun visitThrow(expression: IrThrow, data: Nothing?): IrExpression = visitThrow(expression)

    open fun visitErrorDeclaration(declaration: IrErrorDeclaration): IrStatement = visitDeclaration(declaration)
    override final fun visitErrorDeclaration(declaration: IrErrorDeclaration, data: Nothing?): IrStatement = visitErrorDeclaration(declaration)

    open fun visitErrorExpression(expression: IrErrorExpression): IrExpression = visitExpression(expression)
    override final fun visitErrorExpression(expression: IrErrorExpression, data: Nothing?): IrExpression = visitErrorExpression(expression)

    open fun visitErrorCallExpression(expression: IrErrorCallExpression): IrExpression = visitErrorExpression(expression)
    override final fun visitErrorCallExpression(expression: IrErrorCallExpression, data: Nothing?): IrExpression = visitErrorCallExpression(expression)

    protected inline fun <T : IrElement> T.transformPostfix(body: T.() -> Unit): T {
        transformChildrenVoid()
        this.body()
        return this
    }

    protected fun IrElement.transformChildrenVoid() {
        transformChildrenVoid(this@IrElementTransformerVoid)
    }
}

fun IrElement.transformChildrenVoid(transformer: IrElementTransformerVoid) {
    transformChildren(transformer, null)
}