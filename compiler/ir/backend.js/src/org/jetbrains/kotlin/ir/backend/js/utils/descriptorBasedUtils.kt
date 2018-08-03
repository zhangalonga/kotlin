/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.utils

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.isAny
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

val IrConstructorSymbol.constructedClassType get() = (owner.parent as IrClass).thisReceiver?.type!!

fun ModuleDescriptor.getFunctions(fqName: FqName): List<FunctionDescriptor> {
    return getFunctions(fqName.parent(), fqName.shortName())
}

fun ModuleDescriptor.getFunctions(packageFqName: FqName, name: Name): List<FunctionDescriptor> {
    return getPackage(packageFqName).memberScope.getContributedFunctions(name, NoLookupLocation.FROM_BACKEND).toList()
}

fun ModuleDescriptor.getClassifier(fqName: FqName): ClassifierDescriptor? {
    return getClassifier(fqName.parent(), fqName.shortName())
}

fun ModuleDescriptor.getClassifier(packageFqName: FqName, name: Name): ClassifierDescriptor? {
    return getPackage(packageFqName).memberScope.getContributedClassifier(name, NoLookupLocation.FROM_BACKEND)
}

@Deprecated("Do not use descriptor-based utils")
val CallableMemberDescriptor.propertyIfAccessor
    get() = if (this is PropertyAccessorDescriptor)
        this.correspondingProperty
    else this

// Return is method has no real implementation except fake overrides from Any
fun IrFunction.isFakeOverriddenFromAny(): Boolean {
    if (origin != IrDeclarationOrigin.FAKE_OVERRIDE) {
        return (parent as? IrClass)?.thisReceiver?.type?.isAny() ?: false
    }

    return (this as IrSimpleFunction).overriddenSymbols.all { it.owner.isFakeOverriddenFromAny() }
}

fun IrClassifierSymbol.reference(symbolTable: SymbolTable) = if (!isBound) symbolTable.referenceClassifier(descriptor) else this
