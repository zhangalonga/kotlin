/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.descriptors

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.FunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.KotlinType

interface JvmDescriptorWithExtraFlags {
    val extraFlags: Int
}

class JvmPropertyDescriptorImpl private constructor(
    containingDeclaration: DeclarationDescriptor,
    original: PropertyDescriptor?,
    annotations: Annotations,
    modality: Modality,
    visibility: Visibility,
    override val extraFlags: Int,
    isVar: Boolean,
    name: Name,
    kind: CallableMemberDescriptor.Kind,
    source: SourceElement,
    isLateInit: Boolean,
    isConst: Boolean,
    isExpect: Boolean,
    isActual: Boolean
) : JvmDescriptorWithExtraFlags, PropertyDescriptorImpl(
    containingDeclaration, original, annotations, modality, visibility, isVar,
    name, kind, source, isLateInit, isConst, isExpect, isActual, /* isExternal = */ false, false
) {
    override fun createSubstitutedCopy(
        newOwner: DeclarationDescriptor,
        annotations: Annotations,
        newModality: Modality,
        newVisibility: Visibility,
        original: PropertyDescriptor?,
        kind: CallableMemberDescriptor.Kind,
        newName: Name
    ): PropertyDescriptorImpl =
        JvmPropertyDescriptorImpl(
            newOwner, original, annotations, newModality, newVisibility, extraFlags, isVar, newName, kind,
            SourceElement.NO_SOURCE, isLateInit, isConst, isExpect, isActual
        )

    companion object {
        fun createStaticVal(
            name: Name,
            type: KotlinType,
            containingDeclaration: DeclarationDescriptor,
            annotations: Annotations,
            modality: Modality,
            visibility: Visibility,
            extraFlags: Int,
            source: SourceElement
        ): PropertyDescriptorImpl =
            JvmPropertyDescriptorImpl(
                containingDeclaration, null, annotations, modality, visibility, extraFlags, false, name,
                CallableMemberDescriptor.Kind.SYNTHESIZED, source, false, false, false, false
            ).initialize(type)

        fun createFinalField(
            name: Name,
            type: KotlinType,
            classDescriptor: ClassDescriptor,
            annotations: Annotations,
            visibility: Visibility,
            extraFlags: Int,
            source: SourceElement
        ): PropertyDescriptorImpl =
            JvmPropertyDescriptorImpl(
                classDescriptor, null, annotations, Modality.FINAL, visibility, extraFlags, false, name,
                CallableMemberDescriptor.Kind.SYNTHESIZED, source, false, false, false, false
            ).initialize(type, dispatchReceiverParameter = classDescriptor.thisAsReceiverParameter)
    }
}

class JvmFunctionDescriptorImpl(
    containingDeclaration: DeclarationDescriptor,
    original: FunctionDescriptor?,
    annotations: Annotations,
    name: Name,
    kind: CallableMemberDescriptor.Kind,
    source: SourceElement,
    override val extraFlags: Int
) : JvmDescriptorWithExtraFlags, FunctionDescriptorImpl(
    containingDeclaration, original, annotations,
    name, kind, source
) {
    override fun createSubstitutedCopy(
        newOwner: DeclarationDescriptor,
        original: FunctionDescriptor?,
        kind: CallableMemberDescriptor.Kind,
        newName: Name?,
        annotations: Annotations,
        source: SourceElement
    ): FunctionDescriptorImpl {
        return JvmFunctionDescriptorImpl(
            newOwner, original, annotations, name, kind,
            SourceElement.NO_SOURCE, extraFlags
        )
    }
}
