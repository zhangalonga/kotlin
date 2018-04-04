/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.psi.synthetics

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ClassDescriptorBase
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingContextUtils
import org.jetbrains.kotlin.resolve.DescriptorFactory
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.lazy.LazyClassContext
import org.jetbrains.kotlin.resolve.lazy.data.KtClassLikeInfo
import org.jetbrains.kotlin.resolve.lazy.declarations.ClassMemberDeclarationProvider
import org.jetbrains.kotlin.resolve.lazy.descriptors.ClassResolutionScopesSupport
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyClassMemberScope
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.AbstractClassTypeConstructor
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeConstructor
import java.lang.IllegalStateException

/*
 * This class introduces all attributes that are needed for synthetic classes/object so far.
 * This list may grow in the future, adding more constructor parameters.
 * This class has its own synthetic declaration inside.
 */
class SyntheticClassOrObjectDescriptor(
    c: LazyClassContext,
    parentClassOrObject: KtPureClassOrObject,
    containingDeclaration: DeclarationDescriptor,
    name: Name,
    source: SourceElement,
    outerScope: LexicalScope,
    private val modality: Modality,
    private val visibility: Visibility,
    constructorVisibility: Visibility,
    private val kind: ClassKind,
    private val isCompanionObject: Boolean
) : ClassDescriptorBase(c.storageManager, containingDeclaration, name, source, false), ClassDescriptorWithResolutionScopes {
    val syntheticDeclaration: KtPureClassOrObject = SyntheticDeclaration(parentClassOrObject, name.asString())

    private val thisDescriptor: SyntheticClassOrObjectDescriptor get() = this // code readability
    private val typeConstructor = SyntheticTypeConstructor(c.storageManager)
    private val resolutionScopesSupport = ClassResolutionScopesSupport(thisDescriptor, c.storageManager, c.languageVersionSettings, { outerScope })
    private val syntheticSupertypes =
        mutableListOf<KotlinType>().apply { c.syntheticResolveExtension.addSyntheticSupertypes(thisDescriptor, this) }
    private val unsubstitutedMemberScope =
        LazyClassMemberScope(c, SyntheticClassMemberDeclarationProvider(syntheticDeclaration), this, c.trace)
    private val unsubstitutedPrimaryConstructor = createUnsubstitutedPrimaryConstructor(constructorVisibility)

    override val annotations: Annotations get() = Annotations.EMPTY

    override fun getModality(): Modality = modality
    override fun getVisibility(): Visibility = visibility
    override fun getKind(): ClassKind = kind
    override fun isCompanionObject(): Boolean = isCompanionObject
    override fun isInner(): Boolean = false
    override fun isData(): Boolean = false
    override fun isInline(): Boolean = false
    override fun isExpect(): Boolean = false
    override fun isActual(): Boolean = false

    override fun getCompanionObjectDescriptor(): Nothing? = null
    override fun getTypeConstructor(): TypeConstructor = typeConstructor
    override fun getUnsubstitutedPrimaryConstructor(): ClassConstructorDescriptor = unsubstitutedPrimaryConstructor
    override fun getConstructors(): List<ClassConstructorDescriptor> = listOf(unsubstitutedPrimaryConstructor)
    override fun getDeclaredTypeParameters(): List<TypeParameterDescriptor> = emptyList<TypeParameterDescriptor>()
    override fun getStaticScope(): MemberScope.Empty = MemberScope.Empty
    override fun getUnsubstitutedMemberScope(): LazyClassMemberScope = unsubstitutedMemberScope
    override fun getSealedSubclasses(): List<ClassDescriptor> = emptyList<ClassDescriptor>()

    init {
        assert(modality != Modality.SEALED) { "Implement getSealedSubclasses() for this class: ${this::class.java}" }
    }

    override fun getDeclaredCallableMembers(): List<CallableMemberDescriptor> =
        DescriptorUtils.getAllDescriptors(unsubstitutedMemberScope).filterIsInstance<CallableMemberDescriptor>().filter {
            it.kind != CallableMemberDescriptor.Kind.FAKE_OVERRIDE
        }

    override fun getScopeForClassHeaderResolution(): LexicalScope = resolutionScopesSupport.scopeForClassHeaderResolution()
    override fun getScopeForConstructorHeaderResolution(): LexicalScope = resolutionScopesSupport.scopeForConstructorHeaderResolution()
    override fun getScopeForCompanionObjectHeaderResolution(): LexicalScope =
        resolutionScopesSupport.scopeForCompanionObjectHeaderResolution()

    override fun getScopeForMemberDeclarationResolution(): LexicalScope = resolutionScopesSupport.scopeForMemberDeclarationResolution()
    override fun getScopeForStaticMemberDeclarationResolution(): LexicalScope =
        resolutionScopesSupport.scopeForStaticMemberDeclarationResolution()

    override fun getScopeForInitializerResolution(): LexicalScope =
        throw UnsupportedOperationException("Not supported for synthetic class or object")

    override fun toString(): String = "synthetic class " + name.toString() + " in " + containingDeclaration

    private fun createUnsubstitutedPrimaryConstructor(constructorVisibility: Visibility): ClassConstructorDescriptor {
        val constructor = DescriptorFactory.createPrimaryConstructorForObject(thisDescriptor, source)
        constructor.visibility = constructorVisibility
        constructor.returnType = getDefaultType()
        return constructor
    }

    private inner class SyntheticTypeConstructor(storageManager: StorageManager) : AbstractClassTypeConstructor(storageManager) {
        override fun getParameters(): List<TypeParameterDescriptor> = emptyList()
        override fun isDenotable(): Boolean = true
        override fun getDeclarationDescriptor(): ClassDescriptor = thisDescriptor
        override fun computeSupertypes(): Collection<KotlinType> = syntheticSupertypes
        override val supertypeLoopChecker: SupertypeLoopChecker = SupertypeLoopChecker.EMPTY
    }

    private class SyntheticClassMemberDeclarationProvider(
        override val correspondingClassOrObject: KtPureClassOrObject
    ) : ClassMemberDeclarationProvider {
        override val ownerInfo: KtClassLikeInfo? = null
        override fun getDeclarations(kindFilter: DescriptorKindFilter, nameFilter: (Name) -> Boolean): List<KtDeclaration> = emptyList()
        override fun getFunctionDeclarations(name: Name): Collection<KtNamedFunction> = emptyList()
        override fun getPropertyDeclarations(name: Name): Collection<KtProperty> = emptyList()
        override fun getDestructuringDeclarationsEntries(name: Name): Collection<KtDestructuringDeclarationEntry> = emptyList()
        override fun getClassOrObjectDeclarations(name: Name): Collection<KtClassLikeInfo> = emptyList()
        override fun getTypeAliasDeclarations(name: Name): Collection<KtTypeAlias> = emptyList()
        override fun getDeclarationNames() = emptySet<Name>()
    }

    internal inner class SyntheticDeclaration(
        private val _parent: KtPureElement,
        private val _name: String
    ) : KtPureClassOrObject {
        fun descriptor() = thisDescriptor

        override fun getName(): String? = _name
        override fun isLocal(): Boolean = false

        override fun getDeclarations(): List<KtDeclaration> = emptyList()
        override fun getSuperTypeListEntries(): List<KtSuperTypeListEntry> = emptyList()
        override fun getCompanionObjects(): List<KtObjectDeclaration> = emptyList()

        override fun hasExplicitPrimaryConstructor(): Boolean = false
        override fun hasPrimaryConstructor(): Boolean = false
        override fun getPrimaryConstructor(): KtPrimaryConstructor? = null
        override fun getPrimaryConstructorModifierList(): KtModifierList? = null
        override fun getPrimaryConstructorParameters(): List<KtParameter> = emptyList()
        override fun getSecondaryConstructors(): List<KtSecondaryConstructor> = emptyList()

        override fun getPsiOrParent() = _parent.psiOrParent
        override fun getParent() = _parent.psiOrParent
        override fun getContainingKtFile() =
        // in theory `containingKtFile` is `@NotNull` but in practice EA-114080
            _parent.containingKtFile ?: throw IllegalStateException("containingKtFile was null for $_parent of ${_parent.javaClass}")

    }
}

fun KtPureElement.findClassDescriptor(bindingContext: BindingContext): ClassDescriptor = when (this) {
    is PsiElement -> BindingContextUtils.getNotNull(bindingContext, BindingContext.CLASS, this)
    is SyntheticClassOrObjectDescriptor.SyntheticDeclaration -> descriptor()
    else -> throw IllegalArgumentException("$this shall be PsiElement or SyntheticClassOrObjectDescriptor.SyntheticDeclaration")
}
