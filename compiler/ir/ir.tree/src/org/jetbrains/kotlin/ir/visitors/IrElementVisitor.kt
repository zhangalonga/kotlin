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

interface IrElementVisitor<out R, in D> {
    fun visitElement(element: IrElement, data: D): R
    fun visitModuleFragment(declaration: IrModuleFragment, data: D): R = visitElement(declaration, data)
    fun visitPackageFragment(declaration: IrPackageFragment, data: D): R = visitElement(declaration, data)
    fun visitFile(declaration: IrFile, data: D): R = visitPackageFragment(declaration, data)
    fun visitExternalPackageFragment(declaration: IrExternalPackageFragment, data: D): R = visitPackageFragment(declaration, data)

    fun visitDeclaration(declaration: IrDeclaration, data: D): R = visitElement(declaration, data)
    fun visitClass(declaration: IrClass, data: D): R = visitDeclaration(declaration, data)
    fun visitTypeAlias(declaration: IrTypeAlias, data: D): R = visitDeclaration(declaration, data)
    fun visitFunction(declaration: IrFunction, data: D): R = visitDeclaration(declaration, data)
    fun visitSimpleFunction(declaration: IrSimpleFunction, data: D): R = visitFunction(declaration, data)
    fun visitConstructor(declaration: IrConstructor, data: D): R = visitFunction(declaration, data)
    fun visitProperty(declaration: IrProperty, data: D): R = visitDeclaration(declaration, data)
    fun visitField(declaration: IrField, data: D): R = visitDeclaration(declaration, data)
    fun visitLocalDelegatedProperty(declaration: IrLocalDelegatedProperty, data: D): R = visitDeclaration(declaration, data)
    fun visitVariable(declaration: IrVariable, data: D): R = visitDeclaration(declaration, data)
    fun visitEnumEntry(declaration: IrEnumEntry, data: D): R = visitDeclaration(declaration, data)
    fun visitAnonymousInitializer(declaration: IrAnonymousInitializer, data: D): R = visitDeclaration(declaration, data)
    fun visitTypeParameter(declaration: IrTypeParameter, data: D): R = visitDeclaration(declaration, data)
    fun visitValueParameter(declaration: IrValueParameter, data: D): R = visitDeclaration(declaration, data)

    fun visitBody(body: IrBody, data: D): R = visitElement(body, data)
    fun visitExpressionBody(body: IrExpressionBody, data: D): R = visitBody(body, data)
    fun visitBlockBody(body: IrBlockBody, data: D): R = visitBody(body, data)
    fun visitSyntheticBody(body: IrSyntheticBody, data: D): R = visitBody(body, data)

    fun visitExpression(expression: IrExpression, data: D): R = visitElement(expression, data)
    fun <T> visitConst(expression: IrConst<T>, data: D): R = visitExpression(expression, data)
    fun visitVararg(expression: IrVararg, data: D): R = visitExpression(expression, data)
    fun visitSpreadElement(spread: IrSpreadElement, data: D): R = visitElement(spread, data)

    fun visitContainerExpression(expression: IrContainerExpression, data: D): R = visitExpression(expression, data)
    fun visitBlock(expression: IrBlock, data: D): R = visitContainerExpression(expression, data)
    fun visitComposite(expression: IrComposite, data: D): R = visitContainerExpression(expression, data)
    fun visitStringConcatenation(expression: IrStringConcatenation, data: D): R = visitExpression(expression, data)

    fun visitDeclarationReference(expression: IrDeclarationReference, data: D): R = visitExpression(expression, data)
    fun visitSingletonReference(expression: IrGetSingletonValue, data: D): R = visitDeclarationReference(expression, data)
    fun visitGetObjectValue(expression: IrGetObjectValue, data: D): R = visitSingletonReference(expression, data)
    fun visitGetEnumValue(expression: IrGetEnumValue, data: D): R = visitSingletonReference(expression, data)
    fun visitValueAccess(expression: IrValueAccessExpression, data: D): R = visitDeclarationReference(expression, data)
    fun visitGetValue(expression: IrGetValue, data: D): R = visitValueAccess(expression, data)
    fun visitSetVariable(expression: IrSetVariable, data: D): R = visitValueAccess(expression, data)
    fun visitFieldAccess(expression: IrFieldAccessExpression, data: D): R = visitDeclarationReference(expression, data)
    fun visitGetField(expression: IrGetField, data: D): R = visitFieldAccess(expression, data)
    fun visitSetField(expression: IrSetField, data: D): R = visitFieldAccess(expression, data)

    fun visitMemberAccess(expression: IrMemberAccessExpression, data: D): R = visitExpression(expression, data)
    fun visitFunctionAccess(expression: IrFunctionAccessExpression, data: D): R = visitMemberAccess(expression, data)
    fun visitCall(expression: IrCall, data: D): R = visitFunctionAccess(expression, data)
    fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall, data: D): R = visitFunctionAccess(expression, data)
    fun visitEnumConstructorCall(expression: IrEnumConstructorCall, data: D): R = visitFunctionAccess(expression, data)
    fun visitGetClass(expression: IrGetClass, data: D): R = visitExpression(expression, data)

    fun visitCallableReference(expression: IrCallableReference, data: D): R = visitMemberAccess(expression, data)
    fun visitFunctionReference(expression: IrFunctionReference, data: D): R = visitCallableReference(expression, data)
    fun visitPropertyReference(expression: IrPropertyReference, data: D): R = visitCallableReference(expression, data)
    fun visitLocalDelegatedPropertyReference(expression: IrLocalDelegatedPropertyReference, data: D): R =
        visitCallableReference(expression, data)

    fun visitClassReference(expression: IrClassReference, data: D): R = visitDeclarationReference(expression, data)

    fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall, data: D): R = visitExpression(expression, data)

    fun visitTypeOperator(expression: IrTypeOperatorCall, data: D): R = visitExpression(expression, data)

    fun visitWhen(expression: IrWhen, data: D): R = visitExpression(expression, data)
    fun visitBranch(branch: IrBranch, data: D): R = visitElement(branch, data)
    fun visitElseBranch(branch: IrElseBranch, data: D): R = visitBranch(branch, data)
    fun visitLoop(loop: IrLoop, data: D): R = visitExpression(loop, data)
    fun visitWhileLoop(loop: IrWhileLoop, data: D): R = visitLoop(loop, data)
    fun visitDoWhileLoop(loop: IrDoWhileLoop, data: D): R = visitLoop(loop, data)
    fun visitTry(aTry: IrTry, data: D): R = visitExpression(aTry, data)
    fun visitCatch(aCatch: IrCatch, data: D): R = visitElement(aCatch, data)

    fun visitBreakContinue(jump: IrBreakContinue, data: D): R = visitExpression(jump, data)
    fun visitBreak(jump: IrBreak, data: D): R = visitBreakContinue(jump, data)
    fun visitContinue(jump: IrContinue, data: D): R = visitBreakContinue(jump, data)

    fun visitReturn(expression: IrReturn, data: D): R = visitExpression(expression, data)
    fun visitThrow(expression: IrThrow, data: D): R = visitExpression(expression, data)

    fun visitErrorDeclaration(declaration: IrErrorDeclaration, data: D): R = visitDeclaration(declaration, data)
    fun visitErrorExpression(expression: IrErrorExpression, data: D): R = visitExpression(expression, data)
    fun visitErrorCallExpression(expression: IrErrorCallExpression, data: D): R = visitErrorExpression(expression, data)
}
