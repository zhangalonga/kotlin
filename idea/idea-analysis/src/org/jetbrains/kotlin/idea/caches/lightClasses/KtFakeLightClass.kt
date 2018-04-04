/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.caches.lightClasses

import com.intellij.psi.*
import com.intellij.psi.impl.light.AbstractLightClass
import com.intellij.psi.impl.light.LightMethod
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.LightClassInheritanceHelper
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.load.java.structure.LightClassOriginKind
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.resolve.DescriptorUtils
import javax.swing.Icon

// Used as a placeholder when actual light class does not exist (expect-classes, for example)
// The main purpose is to allow search of inheritors within hierarchies containing such classes
class KtFakeLightClass(override val kotlinOrigin: KtClassOrObject) :
    AbstractLightClass(kotlinOrigin.manager, KotlinLanguage.INSTANCE),
    KtLightClass {
    private val _delegate by lazy { PsiElementFactory.SERVICE.getInstance(kotlinOrigin.project).createClass("dummy") }
    private val _containingClass by lazy { kotlinOrigin.containingClassOrObject?.let { KtFakeLightClass(it) } }

    override val clsDelegate: PsiClass get() = _delegate
    override val originKind: LightClassOriginKind get() = LightClassOriginKind.SOURCE

    override fun getName(): String? = kotlinOrigin.name

    override fun getDelegate(): PsiClass = _delegate
    override fun copy(): KtFakeLightClass = KtFakeLightClass(kotlinOrigin)

    override fun getQualifiedName(): String? = kotlinOrigin.fqName?.asString()
    override fun getContainingClass(): KtFakeLightClass? = _containingClass
    override fun getNavigationElement(): KtClassOrObject = kotlinOrigin
    override fun getIcon(flags: Int): Icon? = kotlinOrigin.getIcon(flags)
    override fun getContainingFile(): PsiFile = kotlinOrigin.containingFile
    override fun getUseScope(): SearchScope = kotlinOrigin.useScope

    override fun isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean {
        LightClassInheritanceHelper.getService(project).isInheritor(this, baseClass, checkDeep).ifSure { return it }

        val baseKtClass = (baseClass as? KtLightClass)?.kotlinOrigin ?: return false
        val baseDescriptor = baseKtClass.resolveToDescriptorIfAny() ?: return false
        val thisDescriptor = kotlinOrigin.resolveToDescriptorIfAny() ?: return false
        return if (checkDeep)
            DescriptorUtils.isSubclass(thisDescriptor, baseDescriptor)
        else
            DescriptorUtils.isDirectSubclass(thisDescriptor, baseDescriptor)
    }
}

class KtFakeLightMethod private constructor(
    val ktDeclaration: KtNamedDeclaration,
    ktClassOrObject: KtClassOrObject
) : LightMethod(
    ktDeclaration.manager,
    PsiElementFactory.SERVICE.getInstance(ktDeclaration.project).createMethod("dummy", PsiType.VOID),
    KtFakeLightClass(ktClassOrObject),
    KotlinLanguage.INSTANCE
), KtLightElement<KtNamedDeclaration, PsiMethod> {
    override val kotlinOrigin: KtNamedDeclaration get() = ktDeclaration
    override val clsDelegate: PsiMethod get() = myMethod

    override fun getName(): String = ktDeclaration.name ?: ""

    override fun getNavigationElement(): KtNamedDeclaration = ktDeclaration
    override fun getIcon(flags: Int): Icon? = ktDeclaration.getIcon(flags)
    override fun getUseScope(): SearchScope = ktDeclaration.useScope

    companion object {
        fun get(ktDeclaration: KtNamedDeclaration): KtFakeLightMethod? {
            val ktClassOrObject = ktDeclaration.containingClassOrObject ?: return null
            return KtFakeLightMethod(ktDeclaration, ktClassOrObject)
        }
    }
}