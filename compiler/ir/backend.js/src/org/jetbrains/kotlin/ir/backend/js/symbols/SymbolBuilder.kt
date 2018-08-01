/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.symbols

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.ir.descriptors.IrTemporaryVariableDescriptorImpl
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.name.Name

object JsSymbolBuilder {

    fun buildTempVar(containingDeclaration: DeclarationDescriptor, type: IrType, name: String? = null, mutable: Boolean = false): IrVariableSymbol {
        return IrVariableSymbolImpl(
            IrTemporaryVariableDescriptorImpl(
                containingDeclaration,
                Name.identifier(name ?: "tmp"),
                type.toKotlinType(), mutable
            )
        )
    }
}