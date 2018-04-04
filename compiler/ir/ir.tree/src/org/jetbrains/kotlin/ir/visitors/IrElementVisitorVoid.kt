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
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*

interface IrElementVisitorVoid : IrElementVisitor<Unit, Nothing?> {
    fun visitElement(element: IrElement)
    override fun visitElement(element: IrElement, data: Nothing?): Unit = visitElement(element)

    fun visitModuleFragment(declaration: IrModuleFragment): Unit = visitElement(declaration)
    override fun visitModuleFragment(declaration: IrModuleFragment, data: Nothing?): Unit = visitModuleFragment(declaration)

    fun visitPackageFragment(declaration: IrPackageFragment): Unit = visitElement(declaration)
    override fun visitPackageFragment(declaration: IrPackageFragment, data: Nothing?): Unit = visitPackageFragment(declaration)

    fun visitExternalPackageFragment(declaration: IrExternalPackageFragment): Unit = visitPackageFragment(declaration)
    override fun visitExternalPackageFragment(declaration: IrExternalPackageFragment, data: Nothing?): Unit =
        visitExternalPackageFragment(declaration)

    fun visitFile(declaration: IrFile): Unit = visitPackageFragment(declaration)
    override fun visitFile(declaration: IrFile, data: Nothing?): Unit = visitFile(declaration)

    fun visitDeclaration(declaration: IrDeclaration): Unit = visitElement(declaration)
    override fun visitDeclaration(declaration: IrDeclaration, data: Nothing?): Unit = visitDeclaration(declaration)

    fun visitClass(declaration: IrClass): Unit = visitDeclaration(declaration)
    override fun visitClass(declaration: IrClass, data: Nothing?): Unit = visitClass(declaration)

    fun visitTypeAlias(declaration: IrTypeAlias): Unit = visitDeclaration(declaration)
    override fun visitTypeAlias(declaration: IrTypeAlias, data: Nothing?): Unit = visitTypeAlias(declaration)

    fun visitFunction(declaration: IrFunction): Unit = visitDeclaration(declaration)
    override fun visitFunction(declaration: IrFunction, data: Nothing?): Unit = visitFunction(declaration)

    fun visitSimpleFunction(declaration: IrSimpleFunction): Unit = visitFunction(declaration)
    override fun visitSimpleFunction(declaration: IrSimpleFunction, data: Nothing?): Unit = visitSimpleFunction(declaration)

    fun visitConstructor(declaration: IrConstructor): Unit = visitFunction(declaration)
    override fun visitConstructor(declaration: IrConstructor, data: Nothing?): Unit = visitConstructor(declaration)

    fun visitProperty(declaration: IrProperty): Unit = visitDeclaration(declaration)
    override fun visitProperty(declaration: IrProperty, data: Nothing?): Unit = visitProperty(declaration)

    fun visitField(declaration: IrField): Unit = visitDeclaration(declaration)
    override fun visitField(declaration: IrField, data: Nothing?): Unit = visitField(declaration)

    fun visitLocalDelegatedProperty(declaration: IrLocalDelegatedProperty): Unit = visitDeclaration(declaration)
    override fun visitLocalDelegatedProperty(declaration: IrLocalDelegatedProperty, data: Nothing?): Unit =
        visitLocalDelegatedProperty(declaration)

    fun visitVariable(declaration: IrVariable): Unit = visitDeclaration(declaration)
    override fun visitVariable(declaration: IrVariable, data: Nothing?): Unit = visitVariable(declaration)

    fun visitEnumEntry(declaration: IrEnumEntry): Unit = visitDeclaration(declaration)
    override fun visitEnumEntry(declaration: IrEnumEntry, data: Nothing?): Unit = visitEnumEntry(declaration)

    fun visitAnonymousInitializer(declaration: IrAnonymousInitializer): Unit = visitDeclaration(declaration)
    override fun visitAnonymousInitializer(declaration: IrAnonymousInitializer, data: Nothing?): Unit = visitAnonymousInitializer(declaration)

    fun visitTypeParameter(declaration: IrTypeParameter): Unit = visitDeclaration(declaration)
    override fun visitTypeParameter(declaration: IrTypeParameter, data: Nothing?): Unit = visitTypeParameter(declaration)

    fun visitValueParameter(declaration: IrValueParameter): Unit = visitDeclaration(declaration)
    override fun visitValueParameter(declaration: IrValueParameter, data: Nothing?): Unit = visitValueParameter(declaration)

    fun visitBody(body: IrBody): Unit = visitElement(body)
    override fun visitBody(body: IrBody, data: Nothing?): Unit = visitBody(body)

    fun visitExpressionBody(body: IrExpressionBody): Unit = visitBody(body)
    override fun visitExpressionBody(body: IrExpressionBody, data: Nothing?): Unit = visitExpressionBody(body)

    fun visitBlockBody(body: IrBlockBody): Unit = visitBody(body)
    override fun visitBlockBody(body: IrBlockBody, data: Nothing?): Unit = visitBlockBody(body)

    fun visitSyntheticBody(body: IrSyntheticBody): Unit = visitBody(body)
    override fun visitSyntheticBody(body: IrSyntheticBody, data: Nothing?): Unit = visitSyntheticBody(body)

    fun visitExpression(expression: IrExpression): Unit = visitElement(expression)
    override fun visitExpression(expression: IrExpression, data: Nothing?): Unit = visitExpression(expression)

    fun <T> visitConst(expression: IrConst<T>): Unit = visitExpression(expression)
    override fun <T> visitConst(expression: IrConst<T>, data: Nothing?): Unit = visitConst(expression)

    fun visitVararg(expression: IrVararg): Unit = visitExpression(expression)
    override fun visitVararg(expression: IrVararg, data: Nothing?): Unit = visitVararg(expression)

    fun visitSpreadElement(spread: IrSpreadElement): Unit = visitElement(spread)
    override fun visitSpreadElement(spread: IrSpreadElement, data: Nothing?): Unit = visitSpreadElement(spread)

    fun visitContainerExpression(expression: IrContainerExpression): Unit = visitExpression(expression)
    override fun visitContainerExpression(expression: IrContainerExpression, data: Nothing?): Unit = visitContainerExpression(expression)

    fun visitComposite(expression: IrComposite): Unit = visitContainerExpression(expression)
    override fun visitComposite(expression: IrComposite, data: Nothing?): Unit = visitComposite(expression)

    fun visitBlock(expression: IrBlock): Unit = visitContainerExpression(expression)
    override fun visitBlock(expression: IrBlock, data: Nothing?): Unit = visitBlock(expression)

    fun visitStringConcatenation(expression: IrStringConcatenation): Unit = visitExpression(expression)
    override fun visitStringConcatenation(expression: IrStringConcatenation, data: Nothing?): Unit = visitStringConcatenation(expression)

    fun visitDeclarationReference(expression: IrDeclarationReference): Unit = visitExpression(expression)
    override fun visitDeclarationReference(expression: IrDeclarationReference, data: Nothing?): Unit = visitDeclarationReference(expression)

    fun visitSingletonReference(expression: IrGetSingletonValue): Unit = visitDeclarationReference(expression)
    override fun visitSingletonReference(expression: IrGetSingletonValue, data: Nothing?): Unit = visitSingletonReference(expression)

    fun visitGetObjectValue(expression: IrGetObjectValue): Unit = visitSingletonReference(expression)
    override fun visitGetObjectValue(expression: IrGetObjectValue, data: Nothing?): Unit = visitGetObjectValue(expression)

    fun visitGetEnumValue(expression: IrGetEnumValue): Unit = visitSingletonReference(expression)
    override fun visitGetEnumValue(expression: IrGetEnumValue, data: Nothing?): Unit = visitGetEnumValue(expression)

    fun visitVariableAccess(expression: IrValueAccessExpression): Unit = visitDeclarationReference(expression)
    override fun visitValueAccess(expression: IrValueAccessExpression, data: Nothing?): Unit = visitVariableAccess(expression)

    fun visitGetValue(expression: IrGetValue): Unit = visitVariableAccess(expression)
    override fun visitGetValue(expression: IrGetValue, data: Nothing?): Unit = visitGetValue(expression)

    fun visitSetVariable(expression: IrSetVariable): Unit = visitVariableAccess(expression)
    override fun visitSetVariable(expression: IrSetVariable, data: Nothing?): Unit = visitSetVariable(expression)

    fun visitFieldAccess(expression: IrFieldAccessExpression): Unit = visitDeclarationReference(expression)
    override fun visitFieldAccess(expression: IrFieldAccessExpression, data: Nothing?): Unit = visitFieldAccess(expression)

    fun visitGetField(expression: IrGetField): Unit = visitFieldAccess(expression)
    override fun visitGetField(expression: IrGetField, data: Nothing?): Unit = visitGetField(expression)

    fun visitSetField(expression: IrSetField): Unit = visitFieldAccess(expression)
    override fun visitSetField(expression: IrSetField, data: Nothing?): Unit = visitSetField(expression)

    fun visitMemberAccess(expression: IrMemberAccessExpression): Unit = visitExpression(expression)
    override fun visitMemberAccess(expression: IrMemberAccessExpression, data: Nothing?): Unit = visitMemberAccess(expression)

    fun visitFunctionAccess(expression: IrFunctionAccessExpression): Unit = visitMemberAccess(expression)
    override fun visitFunctionAccess(expression: IrFunctionAccessExpression, data: Nothing?): Unit = visitFunctionAccess(expression)

    fun visitCall(expression: IrCall): Unit = visitFunctionAccess(expression)
    override fun visitCall(expression: IrCall, data: Nothing?): Unit = visitCall(expression)

    fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall): Unit = visitFunctionAccess(expression)
    override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall, data: Nothing?): Unit =
        visitDelegatingConstructorCall(expression)

    fun visitEnumConstructorCall(expression: IrEnumConstructorCall): Unit = visitFunctionAccess(expression)
    override fun visitEnumConstructorCall(expression: IrEnumConstructorCall, data: Nothing?): Unit = visitEnumConstructorCall(expression)

    fun visitGetClass(expression: IrGetClass): Unit = visitExpression(expression)
    override fun visitGetClass(expression: IrGetClass, data: Nothing?): Unit = visitGetClass(expression)

    fun visitCallableReference(expression: IrCallableReference): Unit = visitMemberAccess(expression)
    override fun visitCallableReference(expression: IrCallableReference, data: Nothing?): Unit = visitCallableReference(expression)

    fun visitFunctionReference(expression: IrFunctionReference): Unit = visitCallableReference(expression)
    override fun visitFunctionReference(expression: IrFunctionReference, data: Nothing?): Unit = visitFunctionReference(expression)

    fun visitPropertyReference(expression: IrPropertyReference): Unit = visitCallableReference(expression)
    override fun visitPropertyReference(expression: IrPropertyReference, data: Nothing?): Unit = visitPropertyReference(expression)

    fun visitLocalDelegatedPropertyReference(expression: IrLocalDelegatedPropertyReference): Unit = visitCallableReference(expression)
    override fun visitLocalDelegatedPropertyReference(expression: IrLocalDelegatedPropertyReference, data: Nothing?): Unit =
        visitLocalDelegatedPropertyReference(expression)

    fun visitClassReference(expression: IrClassReference): Unit = visitDeclarationReference(expression)
    override fun visitClassReference(expression: IrClassReference, data: Nothing?): Unit = visitClassReference(expression)

    fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall): Unit = visitExpression(expression)
    override fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall, data: Nothing?): Unit =
        visitInstanceInitializerCall(expression)

    fun visitTypeOperator(expression: IrTypeOperatorCall): Unit = visitExpression(expression)
    override fun visitTypeOperator(expression: IrTypeOperatorCall, data: Nothing?): Unit = visitTypeOperator(expression)

    fun visitWhen(expression: IrWhen): Unit = visitExpression(expression)
    override fun visitWhen(expression: IrWhen, data: Nothing?): Unit = visitWhen(expression)

    fun visitBranch(branch: IrBranch): Unit = visitElement(branch)
    override fun visitBranch(branch: IrBranch, data: Nothing?): Unit = visitBranch(branch)

    fun visitElseBranch(branch: IrElseBranch): Unit = visitBranch(branch)
    override fun visitElseBranch(branch: IrElseBranch, data: Nothing?): Unit = visitElseBranch(branch)

    fun visitLoop(loop: IrLoop): Unit = visitExpression(loop)
    override fun visitLoop(loop: IrLoop, data: Nothing?): Unit = visitLoop(loop)

    fun visitWhileLoop(loop: IrWhileLoop): Unit = visitLoop(loop)
    override fun visitWhileLoop(loop: IrWhileLoop, data: Nothing?): Unit = visitWhileLoop(loop)

    fun visitDoWhileLoop(loop: IrDoWhileLoop): Unit = visitLoop(loop)
    override fun visitDoWhileLoop(loop: IrDoWhileLoop, data: Nothing?): Unit = visitDoWhileLoop(loop)

    fun visitTry(aTry: IrTry): Unit = visitExpression(aTry)
    override fun visitTry(aTry: IrTry, data: Nothing?): Unit = visitTry(aTry)

    fun visitCatch(aCatch: IrCatch): Unit = visitElement(aCatch)
    override fun visitCatch(aCatch: IrCatch, data: Nothing?): Unit = visitCatch(aCatch)

    fun visitBreakContinue(jump: IrBreakContinue): Unit = visitExpression(jump)
    override fun visitBreakContinue(jump: IrBreakContinue, data: Nothing?): Unit = visitBreakContinue(jump)

    fun visitBreak(jump: IrBreak): Unit = visitBreakContinue(jump)
    override fun visitBreak(jump: IrBreak, data: Nothing?): Unit = visitBreak(jump)

    fun visitContinue(jump: IrContinue): Unit = visitBreakContinue(jump)
    override fun visitContinue(jump: IrContinue, data: Nothing?): Unit = visitContinue(jump)

    fun visitReturn(expression: IrReturn): Unit = visitExpression(expression)
    override fun visitReturn(expression: IrReturn, data: Nothing?): Unit = visitReturn(expression)

    fun visitThrow(expression: IrThrow): Unit = visitExpression(expression)
    override fun visitThrow(expression: IrThrow, data: Nothing?): Unit = visitThrow(expression)

    fun visitErrorDeclaration(declaration: IrErrorDeclaration): Unit = visitDeclaration(declaration)
    override fun visitErrorDeclaration(declaration: IrErrorDeclaration, data: Nothing?): Unit = visitErrorDeclaration(declaration)

    fun visitErrorExpression(expression: IrErrorExpression): Unit = visitExpression(expression)
    override fun visitErrorExpression(expression: IrErrorExpression, data: Nothing?): Unit = visitErrorExpression(expression)

    fun visitErrorCallExpression(expression: IrErrorCallExpression): Unit = visitErrorExpression(expression)
    override fun visitErrorCallExpression(expression: IrErrorCallExpression, data: Nothing?): Unit = visitErrorCallExpression(expression)
}

fun IrElement.acceptVoid(visitor: IrElementVisitorVoid) {
    accept(visitor, null)
}

fun IrElement.acceptChildrenVoid(visitor: IrElementVisitorVoid) {
    acceptChildren(visitor, null)
}