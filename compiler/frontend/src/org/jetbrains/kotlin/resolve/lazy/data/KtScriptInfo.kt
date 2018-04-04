/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.resolve.lazy.data

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

class KtScriptInfo(
    val script: KtScript
) : KtClassLikeInfo {
    override fun getContainingPackageFqName(): FqName = script.fqName.parent()
    override fun getModifierList(): Nothing? = null
    override fun getCompanionObjects(): List<KtObjectDeclaration> = listOf<KtObjectDeclaration>()
    override fun getScopeAnchor(): KtScript = script
    override fun getCorrespondingClassOrObject(): Nothing? = null
    override fun getTypeParameterList(): Nothing? = null
    override fun getPrimaryConstructorParameters(): List<KtParameter> = listOf<KtParameter>()
    override fun getClassKind(): ClassKind = ClassKind.CLASS
    override fun getDeclarations(): List<KtDeclaration> = script.declarations
    override fun getDanglingAnnotations(): List<KtAnnotationEntry> = listOf<KtAnnotationEntry>()
}