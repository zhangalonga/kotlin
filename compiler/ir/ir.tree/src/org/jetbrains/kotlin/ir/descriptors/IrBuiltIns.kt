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

package org.jetbrains.kotlin.ir.descriptors

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.TypeParameterDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.impl.IrExternalPackageFragmentImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrExternalPackageFragmentSymbolImpl
import org.jetbrains.kotlin.ir.util.DeclarationStubGenerator
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.makeNullable

class IrBuiltIns(val builtIns: KotlinBuiltIns) {
    private val packageFragment = IrBuiltinsPackageFragmentDescriptorImpl(builtIns.builtInsModule, KOTLIN_INTERNAL_IR_FQN)
    val irBuiltInsExternalPackageFragment: IrExternalPackageFragmentImpl = IrExternalPackageFragmentImpl(IrExternalPackageFragmentSymbolImpl(packageFragment))

    private val stubBuilder = DeclarationStubGenerator(SymbolTable(), IrDeclarationOrigin.IR_BUILTINS_STUB)

    private fun defineOperator(name: String, returnType: KotlinType, valueParameterTypes: List<KotlinType>): IrSimpleFunction {
        val operatorDescriptor = IrSimpleBuiltinOperatorDescriptorImpl(packageFragment, Name.identifier(name), returnType)
        for ((i, valueParameterType) in valueParameterTypes.withIndex()) {
            operatorDescriptor.addValueParameter(
                IrBuiltinValueParameterDescriptorImpl(operatorDescriptor, Name.identifier("arg$i"), i, valueParameterType)
            )
        }
        return addStubToPackageFragment(operatorDescriptor)
    }

    private fun addStubToPackageFragment(descriptor: SimpleFunctionDescriptor): IrSimpleFunction {
        val irSimpleFunction = stubBuilder.generateFunctionStub(descriptor)
        irBuiltInsExternalPackageFragment.declarations.add(irSimpleFunction)
        irSimpleFunction.parent = irBuiltInsExternalPackageFragment
        return irSimpleFunction
    }

    private fun <T : SimpleFunctionDescriptor> T.addStub(): IrSimpleFunction =
        addStubToPackageFragment(this)

    private fun defineComparisonOperator(name: String, operandType: KotlinType) =
        defineOperator(name, bool, listOf(operandType, operandType))

    private fun List<SimpleType>.defineComparisonOperatorForEachType(name: String) =
        associate { it to defineComparisonOperator(name, it) }

    val bool: SimpleType = builtIns.booleanType
    val any: SimpleType = builtIns.anyType
    val anyN: SimpleType = builtIns.nullableAnyType
    val char: SimpleType = builtIns.charType
    val byte: SimpleType = builtIns.byteType
    val short: SimpleType = builtIns.shortType
    val int: SimpleType = builtIns.intType
    val long: SimpleType = builtIns.longType
    val float: SimpleType = builtIns.floatType
    val double: SimpleType = builtIns.doubleType
    val nothing: SimpleType = builtIns.nothingType
    val unit: SimpleType = builtIns.unitType
    val string: SimpleType = builtIns.stringType

    val primitiveTypes: List<SimpleType> = listOf(bool, char, byte, short, int, long, float, double)
    val primitiveTypesWithComparisons: List<SimpleType> = listOf(int, long, float, double)
    val primitiveFloatingPointTypes: List<SimpleType> = listOf(float, double)

    val lessFunByOperandType: Map<SimpleType, IrSimpleFunction> = primitiveTypesWithComparisons.defineComparisonOperatorForEachType("less")
    val lessOrEqualFunByOperandType: Map<SimpleType, IrSimpleFunction> = primitiveTypesWithComparisons.defineComparisonOperatorForEachType("lessOrEqual")
    val greaterOrEqualFunByOperandType: Map<SimpleType, IrSimpleFunction> = primitiveTypesWithComparisons.defineComparisonOperatorForEachType("greaterOrEqual")
    val greaterFunByOperandType: Map<SimpleType, IrSimpleFunction> = primitiveTypesWithComparisons.defineComparisonOperatorForEachType("greater")

    val ieee754equalsFunByOperandType: Map<SimpleType, IrSimpleFunction> =
        primitiveFloatingPointTypes.associate {
            it to defineOperator("ieee754equals", bool, listOf(it.makeNullable(), it.makeNullable()))
        }

    val eqeqeqFun: IrSimpleFunction = defineOperator("EQEQEQ", bool, listOf(anyN, anyN))
    val eqeqFun: IrSimpleFunction = defineOperator("EQEQ", bool, listOf(anyN, anyN))
    val throwNpeFun: IrSimpleFunction = defineOperator("THROW_NPE", nothing, listOf())
    val booleanNotFun: IrSimpleFunction = defineOperator("NOT", bool, listOf(bool))
    val noWhenBranchMatchedExceptionFun: IrSimpleFunction = defineOperator("noWhenBranchMatchedException", unit, listOf())

    val eqeqeq: FunctionDescriptor = eqeqeqFun.descriptor
    val eqeq: FunctionDescriptor = eqeqFun.descriptor
    val throwNpe: FunctionDescriptor = throwNpeFun.descriptor
    val booleanNot: FunctionDescriptor = booleanNotFun.descriptor
    val noWhenBranchMatchedException: FunctionDescriptor = noWhenBranchMatchedExceptionFun.descriptor

    val eqeqeqSymbol: IrSimpleFunctionSymbol = eqeqeqFun.symbol
    val eqeqSymbol: IrSimpleFunctionSymbol = eqeqFun.symbol
    val throwNpeSymbol: IrSimpleFunctionSymbol = throwNpeFun.symbol
    val booleanNotSymbol: IrSimpleFunctionSymbol = booleanNotFun.symbol
    val noWhenBranchMatchedExceptionSymbol: IrSimpleFunctionSymbol = noWhenBranchMatchedExceptionFun.symbol

    val enumValueOfFun: IrSimpleFunction = createEnumValueOfFun()
    val enumValueOf: FunctionDescriptor = enumValueOfFun.descriptor
    val enumValueOfSymbol: IrSimpleFunctionSymbol = enumValueOfFun.symbol

    private fun createEnumValueOfFun(): IrSimpleFunction =
        SimpleFunctionDescriptorImpl.create(
            packageFragment,
            Annotations.EMPTY,
            Name.identifier("enumValueOf"),
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            SourceElement.NO_SOURCE
        ).apply {
            val typeParameterT = TypeParameterDescriptorImpl.createWithDefaultBound(
                this, Annotations.EMPTY, true, Variance.INVARIANT, Name.identifier("T"), 0
            )

            val valueParameterName = ValueParameterDescriptorImpl(
                this, null, 0, Annotations.EMPTY, Name.identifier("name"), builtIns.stringType,
                false, false, false, null, SourceElement.NO_SOURCE
            )

            val returnType = KotlinTypeFactory.simpleType(Annotations.EMPTY, typeParameterT.typeConstructor, listOf(), false)

            initialize(null, null, listOf(typeParameterT), listOf(valueParameterName), returnType, Modality.FINAL, Visibilities.PUBLIC)
        }.addStub()

    val dataClassArrayMemberHashCodeFun: IrSimpleFunction = defineOperator("dataClassArrayMemberHashCode", int, listOf(any))
    val dataClassArrayMemberHashCode: FunctionDescriptor = dataClassArrayMemberHashCodeFun.descriptor
    val dataClassArrayMemberHashCodeSymbol: IrSimpleFunctionSymbol = dataClassArrayMemberHashCodeFun.symbol

    val dataClassArrayMemberToStringFun: IrSimpleFunction = defineOperator("dataClassArrayMemberToString", string, listOf(anyN))
    val dataClassArrayMemberToString: FunctionDescriptor = dataClassArrayMemberToStringFun.descriptor
    val dataClassArrayMemberToStringSymbol: IrSimpleFunctionSymbol = dataClassArrayMemberToStringFun.symbol

    companion object {
        val KOTLIN_INTERNAL_IR_FQN: FqName = FqName("kotlin.internal.ir")
    }
}
