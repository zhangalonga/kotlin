/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.types

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.resolve.DescriptorUtils.getFqName

private fun IrType.isBuiltInClassType(descriptorPredicate: (ClassDescriptor) -> Boolean, hasQuestionMark: Boolean): Boolean {
    if (this !is IrSimpleType) return false
    val classSymbol = this.classifier as? IrClassSymbol ?: return false
    return descriptorPredicate(classSymbol.descriptor) && this.hasQuestionMark == hasQuestionMark
}

private fun IrType.isClassType(fqName: FqNameUnsafe, hasQuestionMark: Boolean): Boolean {
    if (this !is IrSimpleType) return false
    val classSymbol = this.classifier as? IrClassSymbol ?: return false
    return classFqNameEquals(classSymbol.descriptor, fqName) && this.hasQuestionMark == hasQuestionMark
}

private fun classFqNameEquals(descriptor: ClassDescriptor, fqName: FqNameUnsafe): Boolean =
    descriptor.name == fqName.shortName() && fqName == getFqName(descriptor)

fun IrType.isNullableAny(): Boolean = isBuiltInClassType(KotlinBuiltIns::isAny, hasQuestionMark = true)

fun IrType.isString(): Boolean = isClassType(KotlinBuiltIns.FQ_NAMES.string, hasQuestionMark = false)
fun IrType.isArray(): Boolean = isClassType(KotlinBuiltIns.FQ_NAMES.array, hasQuestionMark = false)