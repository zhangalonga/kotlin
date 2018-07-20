/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.builtins

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.*
import org.jetbrains.kotlin.descriptors.impl.EmptyPackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.impl.MutableClassDescriptor
import org.jetbrains.kotlin.descriptors.impl.TypeParameterDescriptorImpl
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.constants.EnumValue
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection
import org.jetbrains.kotlin.types.typeUtil.builtIns
import org.jetbrains.kotlin.utils.sure

val FAKE_CONTINUATION_CLASS_DESCRIPTOR_EXPERIMENTAL =
    MutableClassDescriptor(
        EmptyPackageFragmentDescriptor(ErrorUtils.getErrorModule(), DescriptorUtils.COROUTINES_PACKAGE_FQ_NAME_EXPERIMENTAL),
        ClassKind.INTERFACE, /* isInner = */ false, /* isExternal = */ false,
        DescriptorUtils.CONTINUATION_INTERFACE_FQ_NAME_EXPERIMENTAL.shortName(), SourceElement.NO_SOURCE, LockBasedStorageManager.NO_LOCKS
    ).apply {
        modality = Modality.ABSTRACT
        visibility = Visibilities.PUBLIC
        setTypeParameterDescriptors(
            TypeParameterDescriptorImpl.createWithDefaultBound(
                this, Annotations.EMPTY, false, Variance.IN_VARIANCE, Name.identifier("T"), 0
            ).let(::listOf)
        )
        createTypeConstructor()
    }

val FAKE_CONTINUATION_CLASS_DESCRIPTOR_RELEASE =
    MutableClassDescriptor(
        EmptyPackageFragmentDescriptor(ErrorUtils.getErrorModule(), DescriptorUtils.COROUTINES_PACKAGE_FQ_NAME_RELEASE),
        ClassKind.INTERFACE, /* isInner = */ false, /* isExternal = */ false,
        DescriptorUtils.CONTINUATION_INTERFACE_FQ_NAME_RELEASE.shortName(), SourceElement.NO_SOURCE, LockBasedStorageManager.NO_LOCKS
    ).apply {
        modality = Modality.ABSTRACT
        visibility = Visibilities.PUBLIC
        setTypeParameterDescriptors(
            TypeParameterDescriptorImpl.createWithDefaultBound(
                this, Annotations.EMPTY, false, Variance.IN_VARIANCE, Name.identifier("T"), 0
            ).let(::listOf)
        )
        createTypeConstructor()
    }

val DEPRECATED_ANNOTATION_FQ_NAME = KotlinBuiltIns.BUILT_INS_PACKAGE_FQ_NAME.child(Name.identifier("Deprecated"))

fun transformSuspendFunctionToRuntimeFunctionType(suspendFunType: KotlinType, isReleaseCoroutines: Boolean): SimpleType {
    assert(suspendFunType.isSuspendFunctionType) {
        "This type should be suspend function type: $suspendFunType"
    }

    return createFunctionType(
            suspendFunType.builtIns,
            suspendFunType.annotations,
            suspendFunType.getReceiverTypeFromFunctionType(),
            suspendFunType.getValueParameterTypesFromFunctionType().map(TypeProjection::getType) +
            KotlinTypeFactory.simpleType(
                    Annotations.EMPTY,
                    // Continuation interface is not a part of built-ins anymore, it has been moved to stdlib.
                    // While it must be somewhere in the dependencies, but here we don't have a reference to the module,
                    // and it's rather complicated to inject it by now, so we just use a fake class descriptor.
                    if (isReleaseCoroutines) FAKE_CONTINUATION_CLASS_DESCRIPTOR_RELEASE.typeConstructor
                    else FAKE_CONTINUATION_CLASS_DESCRIPTOR_EXPERIMENTAL.typeConstructor,
                    listOf(suspendFunType.getReturnTypeFromFunctionType().asTypeProjection()), nullable = false
            ),
            // TODO: names
            null,
            suspendFunType.builtIns.nullableAnyType
    ).makeNullableAsSpecified(suspendFunType.isMarkedNullable)
}

fun transformRuntimeFunctionTypeToSuspendFunction(funType: KotlinType, isReleaseCoroutines: Boolean): SimpleType? {
    assert(funType.isFunctionType) {
        "This type should be function type: $funType"
    }

    val continuationArgumentType = funType.getValueParameterTypesFromFunctionType().lastOrNull()?.type ?: return null
    val continuationArgumentFqName = continuationArgumentType.constructor.declarationDescriptor?.fqNameSafe
    if (!isContinuation(continuationArgumentFqName, isReleaseCoroutines)) {
        // Load experimental suspend function type as suspend function type with @Deprecated
        if (isReleaseCoroutines && isContinuation(continuationArgumentFqName, !isReleaseCoroutines)) {
            val module = funType.constructor.declarationDescriptor.sure { "Cannot get declarationDescriptor for $funType" }.module
            val annotations = AnnotationsImpl.create(
                funType.annotations.getAllAnnotations() + AnnotationWithTarget(
                    deprecatedAnnotationDescriptor(module),
                    null
                )
            )
            return createFunctionType(
                funType.builtIns, annotations,
                funType.getReceiverTypeFromFunctionType(),
                funType.getValueParameterTypesFromFunctionType().dropLast(1).map(TypeProjection::getType),
                // TODO: names
                null,
                continuationArgumentType.arguments.single().type,
                suspendFunction = true
            ).makeNullableAsSpecified(funType.isMarkedNullable)
        }
        return funType as? SimpleType
    } else if (continuationArgumentType.arguments.size != 1) {
        return funType as? SimpleType
    }

    val suspendReturnType = continuationArgumentType.arguments.single().type

    return createFunctionType(
            funType.builtIns,
            funType.annotations,
            funType.getReceiverTypeFromFunctionType(),
            funType.getValueParameterTypesFromFunctionType().dropLast(1).map(TypeProjection::getType),
            // TODO: names
            null,
            suspendReturnType,
            suspendFunction = true
    ).makeNullableAsSpecified(funType.isMarkedNullable)
}

fun isContinuation(name: FqName?, isReleaseCoroutines: Boolean): Boolean {
    return if (isReleaseCoroutines) name == DescriptorUtils.CONTINUATION_INTERFACE_FQ_NAME_RELEASE
    else name == DescriptorUtils.CONTINUATION_INTERFACE_FQ_NAME_EXPERIMENTAL
}

fun deprecatedAnnotationDescriptor(module: ModuleDescriptor): AnnotationDescriptor {
    val deprecatedClassDescriptor = module.resolveClassByFqName(
        DEPRECATED_ANNOTATION_FQ_NAME,
        NoLookupLocation.FROM_DESERIALIZATION
    ).sure { "Cannot get class descriptor for @Deprecated" }
    val deprecatedLevelClassDescriptor = module.resolveClassByFqName(
        KotlinBuiltIns.BUILT_INS_PACKAGE_FQ_NAME.child(Name.identifier("DeprecationLevel")),
        NoLookupLocation.FROM_DESERIALIZATION
    ).sure { "Cannot get class descriptor for DeprecatedLevel" }
    val deprecatedLevelError =
        EnumValue(deprecatedLevelClassDescriptor.classId.sure { "Cannot get classId for DeprecatedLevel" }, Name.identifier("ERROR"))
    return AnnotationDescriptorImpl(
        deprecatedClassDescriptor.defaultType,
        mapOf(
            Name.identifier("message") to StringValue("experimental coroutine"),
            Name.identifier("level") to deprecatedLevelError
        ),
        deprecatedClassDescriptor.source
    )
}

fun KotlinType.isExperimentalSuspendFunctionTypeInReleaseEnvironment(): Boolean = isSuspendFunctionTypeOrSubtype &&
        annotations.hasAnnotation(DEPRECATED_ANNOTATION_FQ_NAME)

fun deprecatedAdditionalAnnotation(module: ModuleDescriptor): Annotations {
    return AnnotationsImpl.create(
        listOf(
            AnnotationWithTarget(
                deprecatedAnnotationDescriptor(module),
                null
            )
        )
    )
}