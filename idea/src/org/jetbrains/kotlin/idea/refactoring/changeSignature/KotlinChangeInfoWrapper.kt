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

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ChangeInfo

class KotlinChangeInfoWrapper(delegate: KotlinChangeInfo) : ChangeInfo {
    var delegate: KotlinChangeInfo? = delegate
        private set

    private val method = delegate.method

    override fun getMethod(): PsiElement = method

    override fun isGenerateDelegate(): Boolean = delegate!!.isGenerateDelegate

    override fun getNewName(): String = delegate!!.newName

    override fun isParameterTypesChanged(): Boolean = delegate!!.isParameterTypesChanged

    override fun getNewParameters(): Array<KotlinParameterInfo> = delegate!!.newParameters

    override fun isParameterSetOrOrderChanged(): Boolean = delegate!!.isParameterSetOrOrderChanged

    override fun isReturnTypeChanged(): Boolean = delegate!!.isReturnTypeChanged

    override fun isParameterNamesChanged(): Boolean = delegate!!.isParameterNamesChanged

    override fun isNameChanged(): Boolean = delegate!!.isNameChanged

    override fun getLanguage(): Language = delegate!!.language

    // Only getMethod() may be called after invalidate()
    fun invalidate() {
        delegate = null
    }
}