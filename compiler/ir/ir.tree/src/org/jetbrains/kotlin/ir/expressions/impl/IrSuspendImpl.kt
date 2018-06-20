/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.expressions.impl

import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrSuspendableRoot
import org.jetbrains.kotlin.ir.expressions.IrSuspensionPoint
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.types.KotlinType

class IrSuspensionPointImpl(
    startOffset: Int, endOffset: Int, type: KotlinType,
    override var suspensionPointIdParameter: IrVariable,
    override var result: IrExpression
) : IrExpressionBase(startOffset, endOffset, type), IrSuspensionPoint {

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
        visitor.visitSuspensionPoint(this, data)

    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
        suspensionPointIdParameter.accept(visitor, data)
        result.accept(visitor, data)
    }

    override fun <D> transformChildren(transformer: IrElementTransformer<D>, data: D) {
        suspensionPointIdParameter = suspensionPointIdParameter.transform(transformer, data) as IrVariable
        result = result.transform(transformer, data)
    }
}

class IrSuspendableRootImpl(
    startOffset: Int, endOffset: Int, type: KotlinType,
    override val suspensionPointId: IrVariableSymbol, override val suspensionResult: IrVariableSymbol
) : IrDoWhileLoopImpl(startOffset, endOffset, type, null), IrSuspendableRoot {

    init {
        label = "\$coroutine\$"
    }

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
        visitor.visitSuspendableRoot(this, data)
}