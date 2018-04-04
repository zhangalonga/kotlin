/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.resolve.constants

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationArgumentVisitor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.SimpleType

abstract class ConstantValue<out T>(open val value: T) {
    abstract fun getType(module: ModuleDescriptor): KotlinType

    abstract fun <R, D> accept(visitor: AnnotationArgumentVisitor<R, D>, data: D): R

    override fun equals(other: Any?): Boolean = this === other || value == (other as? ConstantValue<*>)?.value

    override fun hashCode(): Int = value?.hashCode() ?: 0

    override fun toString(): String = value.toString()
}

abstract class IntegerValueConstant<out T> protected constructor(value: T) : ConstantValue<T>(value)

class AnnotationValue(value: AnnotationDescriptor) : ConstantValue<AnnotationDescriptor>(value) {
    override fun getType(module: ModuleDescriptor): KotlinType = value.type

    override fun <R, D> accept(visitor: AnnotationArgumentVisitor<R, D>, data: D): R = visitor.visitAnnotationValue(this, data)
}

class ArrayValue(
        value: List<ConstantValue<*>>,
        private val computeType: (ModuleDescriptor) -> KotlinType
) : ConstantValue<List<ConstantValue<*>>>(value) {
    override fun getType(module: ModuleDescriptor): KotlinType = computeType(module).also { type ->
        assert(KotlinBuiltIns.isArray(type) || KotlinBuiltIns.isPrimitiveArray(type)) { "Type should be an array, but was $type: $value" }
    }

    override fun <R, D> accept(visitor: AnnotationArgumentVisitor<R, D>, data: D): R = visitor.visitArrayValue(this, data)
}

class BooleanValue(value: Boolean) : ConstantValue<Boolean>(value) {
    override fun getType(module: ModuleDescriptor): SimpleType = module.builtIns.booleanType
    override fun <R, D> accept(visitor: AnnotationArgumentVisitor<R, D>, data: D): R = visitor.visitBooleanValue(this, data)
}

class ByteValue(value: Byte) : IntegerValueConstant<Byte>(value) {
    override fun getType(module: ModuleDescriptor): SimpleType = module.builtIns.byteType

    override fun <R, D> accept(visitor: AnnotationArgumentVisitor<R, D>, data: D): R = visitor.visitByteValue(this, data)
    override fun toString(): String = "$value.toByte()"
}

class CharValue(value: Char) : IntegerValueConstant<Char>(value) {
    override fun getType(module: ModuleDescriptor): SimpleType = module.builtIns.charType

    override fun <R, D> accept(visitor: AnnotationArgumentVisitor<R, D>, data: D): R = visitor.visitCharValue(this, data)

    override fun toString(): String = "\\u%04X ('%s')".format(value.toInt(), getPrintablePart(value))

    private fun getPrintablePart(c: Char): String = when (c) {
        '\b' -> "\\b"
        '\t' -> "\\t"
        '\n' -> "\\n"
        //TODO: KT-8507
        12.toChar() -> "\\f"
        '\r' -> "\\r"
        else -> if (isPrintableUnicode(c)) Character.toString(c) else "?"
    }

    private fun isPrintableUnicode(c: Char): Boolean {
        val t = Character.getType(c).toByte()
        return t != Character.UNASSIGNED &&
               t != Character.LINE_SEPARATOR &&
               t != Character.PARAGRAPH_SEPARATOR &&
               t != Character.CONTROL &&
               t != Character.FORMAT &&
               t != Character.PRIVATE_USE &&
               t != Character.SURROGATE
    }
}

class DoubleValue(value: Double) : ConstantValue<Double>(value) {
    override fun getType(module: ModuleDescriptor): SimpleType = module.builtIns.doubleType

    override fun <R, D> accept(visitor: AnnotationArgumentVisitor<R, D>, data: D): R = visitor.visitDoubleValue(this, data)

    override fun toString(): String = "$value.toDouble()"
}

class EnumValue(val enumClassId: ClassId, val enumEntryName: Name) : ConstantValue<Pair<ClassId, Name>>(enumClassId to enumEntryName) {
    override fun getType(module: ModuleDescriptor): KotlinType =
            module.findClassAcrossModuleDependencies(enumClassId)?.takeIf(DescriptorUtils::isEnumClass)?.defaultType
            ?: ErrorUtils.createErrorType("Containing class for error-class based enum entry $enumClassId.$enumEntryName")

    override fun <R, D> accept(visitor: AnnotationArgumentVisitor<R, D>, data: D): R = visitor.visitEnumValue(this, data)

    override fun toString(): String = "${enumClassId.shortClassName}.$enumEntryName"
}

abstract class ErrorValue : ConstantValue<Unit>(Unit) {
    @Deprecated("Should not be called, for this is not a real value, but a indication of an error")
    override val value: Unit
        get() = throw UnsupportedOperationException()

    override fun <R, D> accept(visitor: AnnotationArgumentVisitor<R, D>, data: D): R = visitor.visitErrorValue(this, data)

    class ErrorValueWithMessage(val message: String) : ErrorValue() {
        override fun getType(module: ModuleDescriptor): SimpleType = ErrorUtils.createErrorType(message)

        override fun toString(): String = message
    }

    companion object {
        fun create(message: String): ErrorValue {
            return ErrorValueWithMessage(message)
        }
    }
}

class FloatValue(value: Float) : ConstantValue<Float>(value) {
    override fun getType(module: ModuleDescriptor): SimpleType = module.builtIns.floatType

    override fun <R, D> accept(visitor: AnnotationArgumentVisitor<R, D>, data: D): R = visitor.visitFloatValue(this, data)

    override fun toString(): String = "$value.toFloat()"
}

class IntValue(value: Int) : IntegerValueConstant<Int>(value) {
    override fun getType(module: ModuleDescriptor): SimpleType = module.builtIns.intType

    override fun <R, D> accept(visitor: AnnotationArgumentVisitor<R, D>, data: D): R = visitor.visitIntValue(this, data)
}

class KClassValue(private val type: KotlinType) : ConstantValue<KotlinType>(type) {
    override fun getType(module: ModuleDescriptor): KotlinType = type

    override val value: KotlinType
        get() = type.arguments.single().type

    override fun <R, D> accept(visitor: AnnotationArgumentVisitor<R, D>, data: D): R = visitor.visitKClassValue(this, data)
}

class LongValue(value: Long) : IntegerValueConstant<Long>(value) {
    override fun getType(module: ModuleDescriptor): SimpleType = module.builtIns.longType

    override fun <R, D> accept(visitor: AnnotationArgumentVisitor<R, D>, data: D): R = visitor.visitLongValue(this, data)

    override fun toString(): String = "$value.toLong()"
}

class NullValue : ConstantValue<Void?>(null) {
    override fun getType(module: ModuleDescriptor): SimpleType = module.builtIns.nullableNothingType

    override fun <R, D> accept(visitor: AnnotationArgumentVisitor<R, D>, data: D): R = visitor.visitNullValue(this, data)
}

class ShortValue(value: Short) : IntegerValueConstant<Short>(value) {
    override fun getType(module: ModuleDescriptor): SimpleType = module.builtIns.shortType

    override fun <R, D> accept(visitor: AnnotationArgumentVisitor<R, D>, data: D): R = visitor.visitShortValue(this, data)

    override fun toString(): String = "$value.toShort()"
}

class StringValue(value: String) : ConstantValue<String>(value) {
    override fun getType(module: ModuleDescriptor): SimpleType = module.builtIns.stringType

    override fun <R, D> accept(visitor: AnnotationArgumentVisitor<R, D>, data: D): R = visitor.visitStringValue(this, data)

    override fun toString(): String = "\"$value\""
}
