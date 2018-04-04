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

package org.jetbrains.kotlin.asJava.classes

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.ItemPresentationProviders
import com.intellij.psi.*
import com.intellij.psi.impl.PsiClassImplUtil
import com.intellij.psi.impl.light.AbstractLightClass
import com.intellij.psi.impl.source.ClassInnerStuffCache
import com.intellij.psi.impl.source.PsiExtensibleClass
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.kotlin.asJava.elements.KtLightFieldImpl
import org.jetbrains.kotlin.asJava.elements.KtLightMethodImpl
import org.jetbrains.kotlin.idea.KotlinLanguage

abstract class KtLightClassBase protected constructor(manager: PsiManager)
    : AbstractLightClass(manager, KotlinLanguage.INSTANCE), KtLightClass, PsiExtensibleClass {
    private val myInnersCache = ClassInnerStuffCache(this)

    override fun getDelegate(): PsiClass = clsDelegate

    override fun getFields(): Array<out PsiField> = myInnersCache.fields

    override fun getMethods(): Array<out PsiMethod> = myInnersCache.methods

    override fun getConstructors(): Array<out PsiMethod> = myInnersCache.constructors

    override fun getInnerClasses(): Array<out PsiClass> = myInnersCache.innerClasses

    override fun getAllFields(): Array<out PsiField> = PsiClassImplUtil.getAllFields(this)

    override fun getAllMethods(): Array<out PsiMethod> = PsiClassImplUtil.getAllMethods(this)

    override fun getAllInnerClasses(): Array<out PsiClass> = PsiClassImplUtil.getAllInnerClasses(this)

    override fun findFieldByName(name: String, checkBases: Boolean): PsiField? = myInnersCache.findFieldByName(name, checkBases)

    override fun findMethodsByName(name: String, checkBases: Boolean): Array<out PsiMethod> = myInnersCache.findMethodsByName(name, checkBases)

    override fun findInnerClassByName(name: String, checkBases: Boolean): PsiClass? = myInnersCache.findInnerClassByName(name, checkBases)

    override fun getOwnFields(): List<PsiField> = KtLightFieldImpl.fromClsFields(delegate, this)

    override fun getOwnMethods(): List<PsiMethod> = KtLightMethodImpl.fromClsMethods(delegate, this)

    override fun processDeclarations(
            processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement?, place: PsiElement
    ): Boolean {
        if (isEnum) {
            if (!PsiClassImplUtil.processDeclarationsInEnum(processor, state, myInnersCache)) return false
        }

        return super.processDeclarations(processor, state, lastParent, place)
    }

    override fun getText(): String {
        val origin = kotlinOrigin
        return if (origin == null) "" else origin.text
    }

    override fun getLanguage(): KotlinLanguage = KotlinLanguage.INSTANCE

    override fun getPresentation(): ItemPresentation? = ItemPresentationProviders.getItemPresentation(this)

    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int

    override fun getContext(): PsiElement? = parent
}
