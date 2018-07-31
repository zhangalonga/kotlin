/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.descriptors

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.TypeConstructor
import org.jetbrains.kotlin.types.TypeSubstitutor


abstract class DescriptorWrapper<T : IrDeclaration> : DeclarationDescriptor {

    lateinit var owner: T
    fun bind(declaration: T) { owner = declaration }

    override fun getOriginal() = this

}

abstract class WrappedCallableDescriptor<T : IrDeclaration>: CallableDescriptor, DescriptorWrapper<T>() {
    override fun getOriginal() = this

    override fun substitute(substitutor: TypeSubstitutor): CallableDescriptor {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getOverriddenDescriptors(): Collection<CallableDescriptor> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getSource(): SourceElement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getExtensionReceiverParameter(): ReceiverParameterDescriptor? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getDispatchReceiverParameter(): ReceiverParameterDescriptor? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getTypeParameters(): List<TypeParameterDescriptor> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getReturnType(): KotlinType? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getValueParameters(): MutableList<ValueParameterDescriptor> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun hasStableParameterNames(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun hasSynthesizedParameterNames(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getVisibility(): Visibility {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <R : Any?, D : Any?> accept(visitor: DeclarationDescriptorVisitor<R, D>?, data: D): R {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun acceptVoid(visitor: DeclarationDescriptorVisitor<Void, Void>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val annotations: Annotations = Annotations.EMPTY
}

open class WrappedValueParameterDescriptor : ValueParameterDescriptor, WrappedCallableDescriptor<IrValueParameter>() {

    override val index get() = owner.index
    override val isCrossinline get() = owner.isCrossinline
    override val isNoinline get() = owner.isNoinline
    override val varargElementType get() = owner.varargElementType?.toKotlinType()
    override fun isConst() = false
    override fun isVar() = false

    override fun getContainingDeclaration() = (owner.parent as IrFunction).descriptor
    override fun getType() = owner.type.toKotlinType()
    override fun getName() = owner.name
    override fun declaresDefaultValue() = owner.defaultValue != null
    override fun getCompileTimeInitializer(): ConstantValue<*>? {
        TODO("")
    }

    override fun copy(newOwner: CallableDescriptor, newName: Name, newIndex: Int) = object : WrappedValueParameterDescriptor() {
        override fun getContainingDeclaration() = newOwner as FunctionDescriptor
        override fun getName() = newName
        override val index = newIndex
    }


    override fun getOverriddenDescriptors(): Collection<ValueParameterDescriptor> {
        TODO("Not Implemented")
    }

    override fun getOriginal() = this

    override fun substitute(substitutor: TypeSubstitutor): ValueParameterDescriptor {
        TODO("")
    }
}

open class WrappedTypeParameterDescriptor: TypeParameterDescriptor, WrappedCallableDescriptor<IrTypeParameter>() {
    override fun getName() = owner.name

    override fun isReified(): Boolean {
        return false
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getVariance() = owner.variance

    override fun getUpperBounds() = owner.superTypes.map { it.toKotlinType() }

    override fun getTypeConstructor(): TypeConstructor {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getOriginal() = this

    override fun getIndex() = owner.index

    override fun isCapturedFromOuterDeclaration() = false

    override fun getDefaultType(): SimpleType {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getContainingDeclaration() = (owner.parent as IrDeclaration).descriptor

}

open class WrappedVariableDescriptor: VariableDescriptor, WrappedCallableDescriptor<IrVariable>() {

    override fun getContainingDeclaration() = (owner.parent as IrFunction).descriptor
    override fun getType() = owner.type.toKotlinType()
    override fun getName() = owner.name
    override fun isConst() = owner.isConst
    override fun isVar() = owner.isVar
    override fun isLateInit() = owner.isLateinit

    override fun getCompileTimeInitializer(): ConstantValue<*>? {
        TODO("")
    }

    override fun getOverriddenDescriptors(): Collection<VariableDescriptor> {
        TODO("Not Implemented")
    }

    override fun getOriginal() = this

    override fun substitute(substitutor: TypeSubstitutor): VariableDescriptor {
        TODO("")
    }
}

open class WrappedSimpleFunctionDescriptor : SimpleFunctionDescriptor, WrappedCallableDescriptor<IrSimpleFunction>() {
    override fun getOverriddenDescriptors() = owner.overriddenSymbols.map { it.descriptor }
    override fun getContainingDeclaration() = (owner.parent as IrDeclaration).descriptor
    override fun getModality() = owner.modality
    override fun getName() = owner.name
    override fun getVisibility() = owner.visibility
    override fun getReturnType() = owner.returnType.toKotlinType()
    override fun getDispatchReceiverParameter() = owner.dispatchReceiverParameter?.descriptor as? ReceiverParameterDescriptor
    override fun getTypeParameters() = owner.typeParameters.map { it.descriptor }
    override fun getValueParameters() = owner.valueParameters.mapNotNull { it.descriptor as? ValueParameterDescriptor }.toMutableList()
    override fun isExternal() = owner.isExternal
    override fun isSuspend() = owner.isSuspend
    override fun isTailrec() = owner.isTailrec
    override fun isInline() = owner.isInline

    override fun isExpect() = false
    override fun isActual() = false
    override fun isInfix() = false
    override fun isOperator() = false

    override fun getOriginal() = this
    override fun substitute(substitutor: TypeSubstitutor): SimpleFunctionDescriptor {
        TODO("")
    }
    override fun setOverriddenDescriptors(overriddenDescriptors: MutableCollection<out CallableMemberDescriptor>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getKind() = CallableMemberDescriptor.Kind.SYNTHESIZED

    override fun isHiddenToOvercomeSignatureClash(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun copy(
        newOwner: DeclarationDescriptor?,
        modality: Modality?,
        visibility: Visibility?,
        kind: CallableMemberDescriptor.Kind?,
        copyOverrides: Boolean
    ): SimpleFunctionDescriptor {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isHiddenForResolutionEverywhereBesideSupercalls(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getInitialSignatureDescriptor(): FunctionDescriptor? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <V : Any?> getUserData(key: FunctionDescriptor.UserDataKey<V>?): V? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun newCopyBuilder(): FunctionDescriptor.CopyBuilder<out SimpleFunctionDescriptor> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

open class WrappedClassConstructorDescriptor: ClassConstructorDescriptor, WrappedCallableDescriptor<IrConstructor>() {
    override fun getContainingDeclaration() = (owner.parent as IrClass).descriptor

    override fun getTypeParameters() = owner.typeParameters.map { it.descriptor }
    override fun getValueParameters() = owner.valueParameters.mapNotNull { it.descriptor as? ValueParameterDescriptor }.toMutableList()

    override fun getOriginal() = this

    override fun substitute(substitutor: TypeSubstitutor): ClassConstructorDescriptor {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun copy(
        newOwner: DeclarationDescriptor,
        modality: Modality,
        visibility: Visibility,
        kind: CallableMemberDescriptor.Kind,
        copyOverrides: Boolean
    ): ClassConstructorDescriptor {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getModality() = Modality.FINAL


    override fun setOverriddenDescriptors(overriddenDescriptors: MutableCollection<out CallableMemberDescriptor>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getKind(): CallableMemberDescriptor.Kind {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getConstructedClass() = (owner.parent as IrClass).descriptor

    override fun getName() = owner.name

    override fun getOverriddenDescriptors(): MutableCollection<out FunctionDescriptor> = mutableListOf()

    override fun getInitialSignatureDescriptor(): FunctionDescriptor? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isHiddenToOvercomeSignatureClash(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isOperator() = false

    override fun isInline() = owner.isInline

    override fun isHiddenForResolutionEverywhereBesideSupercalls(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getReturnType() = owner.returnType.toKotlinType()

    override fun isPrimary() = owner.isPrimary

    override fun isExpect() = false

    override fun isTailrec() = false

    override fun isActual() = false

    override fun isInfix() = false

    override fun isSuspend() = false

    override fun <V : Any?> getUserData(key: FunctionDescriptor.UserDataKey<V>?): V? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isExternal() = owner.isExternal

    override fun newCopyBuilder(): FunctionDescriptor.CopyBuilder<out FunctionDescriptor> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}
