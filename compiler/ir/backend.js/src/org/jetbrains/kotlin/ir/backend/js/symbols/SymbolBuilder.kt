/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.symbols

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.FunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.ir.backend.js.utils.createValueParameter
import org.jetbrains.kotlin.ir.descriptors.IrTemporaryVariableDescriptorImpl
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeSubstitutor

object JsSymbolBuilder {
    fun buildValueParameter(containingSymbol: IrSimpleFunctionSymbol, index: Int, type: IrType, name: String? = null) =
        IrValueParameterSymbolImpl(createValueParameter(containingSymbol.descriptor, index, name ?: "param$index", type.toKotlinType()))

    fun buildSimpleFunction(
        containingDeclaration: DeclarationDescriptor,
        name: String,
        annotations: Annotations = Annotations.EMPTY,
        kind: CallableMemberDescriptor.Kind = CallableMemberDescriptor.Kind.SYNTHESIZED,
        source: SourceElement = SourceElement.NO_SOURCE
    ) = IrSimpleFunctionSymbolImpl(
        SimpleFunctionDescriptorImpl.create(
            containingDeclaration,
            annotations,
            Name.identifier(name),
            kind,
            source
        )
    )

    fun copyFunctionSymbol(symbol: IrFunctionSymbol, newName: String) = IrSimpleFunctionSymbolImpl(
        SimpleFunctionDescriptorImpl.create(
            symbol.descriptor.containingDeclaration,
            symbol.descriptor.annotations,
            Name.identifier(newName),
            symbol.descriptor.kind,
            symbol.descriptor.source
        )
    )

    fun buildVar(
        containingDeclaration: DeclarationDescriptor,
        type: IrType,
        name: String,
        mutable: Boolean = false
    ) = IrVariableSymbolImpl(
        LocalVariableDescriptor(
            containingDeclaration,
            Annotations.EMPTY,
            Name.identifier(name),
            type.toKotlinType(),
            mutable,
            false,
            SourceElement.NO_SOURCE
        )
    )

    fun buildTempVar(containingSymbol: IrSymbol, type: IrType, name: String? = null, mutable: Boolean = false) =
        buildTempVar(containingSymbol.descriptor, type, name, mutable)

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


fun IrSimpleFunctionSymbol.initialize(
    receiverParameterType: IrType? = null,
    dispatchParameterDescriptor: ReceiverParameterDescriptor? = null,
    typeParameters: List<TypeParameterDescriptor> = emptyList(),
    valueParameters: List<ValueParameterDescriptor> = emptyList(),
    returnType: IrType? = null,
    modality: Modality = Modality.FINAL,
    visibility: Visibility = Visibilities.LOCAL
) = this.apply {
    (descriptor as FunctionDescriptorImpl).initialize(
        receiverParameterType?.toKotlinType(),
        dispatchParameterDescriptor,
        typeParameters,
        valueParameters,
        returnType?.toKotlinType(),
        modality,
        visibility
    )
}

private fun <T> throwISE(msg: String = ""): T { throw IllegalStateException(msg) }

interface FakeSimpleFunctionDescriptor: SimpleFunctionDescriptor {
    override fun getModality(): Modality = throwISE("modality")

    override fun setOverriddenDescriptors(overriddenDescriptors: MutableCollection<out CallableMemberDescriptor>)
            = throwISE("setOverriddenDescriptors")

    override fun getKind(): CallableMemberDescriptor.Kind = throwISE("kind")

    override fun getName(): Name = throwISE("name")

    override fun getSource() = throwISE("source")

    override fun isHiddenToOvercomeSignatureClash() = throwISE("isHiddenToOvercomeSignatureClash")

    override fun getTypeParameters(): List<TypeParameterDescriptor> = throwISE("getTypeParameters")

    override fun hasSynthesizedParameterNames() = throwISE("hasSynthesizedParameterNames")

    override fun getOverriddenDescriptors() = throwISE("getOverriddenDescriptors")

    override fun isOperator() = throwISE("isOperator")

    override fun isInline() = throwISE("isInline")

    override fun copy(
        newOwner: DeclarationDescriptor?,
        modality: Modality?,
        visibility: Visibility?,
        kind: CallableMemberDescriptor.Kind?,
        copyOverrides: Boolean
    ) = throwISE("copy")

    override fun isHiddenForResolutionEverywhereBesideSupercalls() = throwISE("isHiddenForResolutionEverywhereBesideSupercalls")

    override fun getValueParameters(): List<ValueParameterDescriptor> = throwISE("getValueParameters")

    override fun getVisibility(): Visibility = throwISE("getVisibility")

    override fun getOriginal() = throwISE("getOriginal")

    override fun isExpect() = throwISE("isExpect")

    override fun getContainingDeclaration() = throwISE("getContainingDeclaration")

    override fun substitute(substitutor: TypeSubstitutor) = throwISE("substitute")

    override fun getInitialSignatureDescriptor() = throwISE("getInitialSignatureDescriptor")

    override fun isInfix() = throwISE("isInfix")

    override fun isTailrec() = throwISE("isTailrec")

    override fun isActual() = throwISE("isTailrec")

    override fun isSuspend() = throwISE("isTailrec")

    override fun getExtensionReceiverParameter() = throwISE("isTailrec")

    override fun getDispatchReceiverParameter(): ReceiverParameterDescriptor? = throwISE("isTailrec")

    override fun getReturnType(): KotlinType = throwISE("getReturnType")

    override fun hasStableParameterNames() = throwISE("isTailrec")

    override fun <V : Any?> getUserData(key: FunctionDescriptor.UserDataKey<V>?) = throwISE("isTailrec")

    override fun <R : Any?, D : Any?> accept(visitor: DeclarationDescriptorVisitor<R, D>?, data: D) = throwISE("isTailrec")

    override fun isExternal() = throwISE("isTailrec")

    override fun acceptVoid(visitor: DeclarationDescriptorVisitor<Void, Void>?) = throwISE("isTailrec")

    override fun newCopyBuilder() = throwISE("isTailrec")

    override val annotations: Annotations
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

}