/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.builtins.functions

import org.jetbrains.kotlin.builtins.BuiltInsPackageFragment
import org.jetbrains.kotlin.builtins.KOTLIN_REFLECT_FQ_NAME
import org.jetbrains.kotlin.builtins.KotlinBuiltIns.BUILT_INS_PACKAGE_FQ_NAME
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.AbstractClassDescriptor
import org.jetbrains.kotlin.descriptors.impl.TypeParameterDescriptorImpl
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.*
import java.util.*

/**
 * A [ClassDescriptor] representing the fictitious class for a function type, such as kotlin.Function1 or kotlin.reflect.KFunction2.
 *
 * If the class represents kotlin.Function1, its only supertype is kotlin.Function.
 *
 * If the class represents kotlin.reflect.KFunction1, it has two supertypes: kotlin.Function1 and kotlin.reflect.KFunction.
 * This allows to use both 'invoke' and reflection API on function references obtained by '::'.
 */
class FunctionClassDescriptor(
        private val storageManager: StorageManager,
        private val containingDeclaration: PackageFragmentDescriptor,
        val functionKind: Kind,
        val arity: Int
) : AbstractClassDescriptor(storageManager, functionKind.numberedClassName(arity)) {

    enum class Kind(val packageFqName: FqName, val classNamePrefix: String) {
        Function(BUILT_INS_PACKAGE_FQ_NAME, "Function"),
        SuspendFunction(BUILT_INS_PACKAGE_FQ_NAME, "SuspendFunction"),
        KFunction(KOTLIN_REFLECT_FQ_NAME, "KFunction");

        fun numberedClassName(arity: Int): Name = Name.identifier("$classNamePrefix$arity")

        companion object {
            fun byClassNamePrefix(packageFqName: FqName, className: String): Kind? =
                    Kind.values().firstOrNull { it.packageFqName == packageFqName && className.startsWith(it.classNamePrefix) }
        }
    }

    private val typeConstructor = FunctionTypeConstructor()
    private val memberScope = FunctionClassScope(storageManager, this)

    private val parameters: List<TypeParameterDescriptor>

    init {
        val result = ArrayList<TypeParameterDescriptor>()

        fun typeParameter(variance: Variance, name: String) {
            result.add(TypeParameterDescriptorImpl.createWithDefaultBound(
                    this@FunctionClassDescriptor, Annotations.EMPTY, false, variance, Name.identifier(name), result.size
            ))
        }

        (1..arity).map { i ->
            typeParameter(Variance.IN_VARIANCE, "P$i")
        }

        typeParameter(Variance.OUT_VARIANCE, "R")

        parameters = result.toList()
    }

    override fun getContainingDeclaration(): PackageFragmentDescriptor = containingDeclaration

    override fun getStaticScope(): MemberScope.Empty = MemberScope.Empty

    override fun getTypeConstructor(): TypeConstructor = typeConstructor

    override fun getUnsubstitutedMemberScope(): FunctionClassScope = memberScope

    override fun getCompanionObjectDescriptor(): Nothing? = null
    override fun getConstructors(): List<ClassConstructorDescriptor> = emptyList<ClassConstructorDescriptor>()
    override fun getKind(): ClassKind = ClassKind.INTERFACE
    override fun getModality(): Modality = Modality.ABSTRACT
    override fun getUnsubstitutedPrimaryConstructor(): Nothing? = null
    override fun getVisibility(): Visibility = Visibilities.PUBLIC
    override fun isCompanionObject(): Boolean = false
    override fun isInner(): Boolean = false
    override fun isData(): Boolean = false
    override fun isInline(): Boolean = false
    override fun isExpect(): Boolean = false
    override fun isActual(): Boolean = false
    override fun isExternal(): Boolean = false
    override val annotations: Annotations get() = Annotations.EMPTY
    override fun getSource(): SourceElement = SourceElement.NO_SOURCE
    override fun getSealedSubclasses(): List<ClassDescriptor> = emptyList()

    override fun getDeclaredTypeParameters(): List<TypeParameterDescriptor> = parameters

    private inner class FunctionTypeConstructor : AbstractClassTypeConstructor(storageManager) {
        override fun computeSupertypes(): Collection<KotlinType> {
            val result = ArrayList<KotlinType>(2)

            fun add(packageFragment: PackageFragmentDescriptor, name: Name) {
                val descriptor = packageFragment.getMemberScope().getContributedClassifier(name, NoLookupLocation.FROM_BUILTINS) as? ClassDescriptor
                                 ?: error("Class $name not found in $packageFragment")

                val typeConstructor = descriptor.typeConstructor

                // Substitute all type parameters of the super class with our last type parameters
                val arguments = parameters.takeLast(typeConstructor.parameters.size).map {
                    TypeProjectionImpl(it.defaultType)
                }

                result.add(KotlinTypeFactory.simpleNotNullType(Annotations.EMPTY, descriptor, arguments))
            }


            if (functionKind == Kind.SuspendFunction) {
                // SuspendFunction$N<...> <: Any
                result.add(containingDeclaration.builtIns.anyType)
            }
            else {
                // Add unnumbered base class, e.g. Function for Function{n}, KFunction for KFunction{n}
                add(containingDeclaration, Name.identifier(functionKind.classNamePrefix))
            }

            // For KFunction{n}, add corresponding numbered Function{n} class, e.g. Function2 for KFunction2
            if (functionKind == Kind.KFunction) {
                val packageView = containingDeclaration.containingDeclaration.getPackage(BUILT_INS_PACKAGE_FQ_NAME)
                val kotlinPackageFragment = packageView.fragments.filterIsInstance<BuiltInsPackageFragment>().first()

                add(kotlinPackageFragment, Kind.Function.numberedClassName(arity))
            }

            return result.toList()
        }

        override fun getParameters() = this@FunctionClassDescriptor.parameters

        override fun getDeclarationDescriptor() = this@FunctionClassDescriptor
        override fun isDenotable() = true

        override fun toString() = declarationDescriptor.toString()

        override val supertypeLoopChecker: SupertypeLoopChecker
            get() = SupertypeLoopChecker.EMPTY
    }

    override fun toString(): String = name.asString()
}
