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

package org.jetbrains.kotlin.asJava.elements

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.ItemPresentationProviders
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtElement

abstract class KtLightElementBase(private val parent: PsiElement): LightElement(parent.manager, KotlinLanguage.INSTANCE) {
    override fun toString(): String = "${this.javaClass.simpleName} of $parent"
    override fun getParent(): PsiElement = parent

    abstract val kotlinOrigin: KtElement?

    override fun getText(): String = kotlinOrigin?.text ?: ""
    override fun getTextRange(): TextRange = kotlinOrigin?.textRange ?: TextRange.EMPTY_RANGE
    override fun isWritable(): Boolean = kotlinOrigin?.isWritable ?: false
    override fun getNavigationElement(): PsiElement = kotlinOrigin?.navigationElement ?: this
    override fun getUseScope(): SearchScope = kotlinOrigin?.useScope ?: super.getUseScope()
    override fun getContainingFile(): PsiFile = parent.containingFile
    override fun getPresentation(): ItemPresentation? = (kotlinOrigin ?: this).let { ItemPresentationProviders.getItemPresentation(it) }
    override fun isValid(): Boolean = parent.isValid
}