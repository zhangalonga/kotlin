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

package org.jetbrains.kotlin.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.stubs.KotlinScriptStub
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes
import org.jetbrains.kotlin.script.getScriptDefinition
import kotlin.LazyThreadSafetyMode.PUBLICATION

class KtScript : KtNamedDeclarationStub<KotlinScriptStub>, KtDeclarationContainer {

    val kotlinScriptDefinition by lazy(PUBLICATION) {
        getScriptDefinition(containingKtFile)
            ?: throw NullPointerException("Should not parse a script without definition: " + containingKtFile.virtualFile.path)
    }

    val body: KtScriptBody
        get() = findNotNullChildByClass(KtScriptBody::class.java)

    val blockExpression: KtBlockExpression
        get() = PsiTreeUtil.getChildOfType(body, KtBlockExpression::class.java)!!

    constructor(node: ASTNode) : super(node)

    constructor(stub: KotlinScriptStub) : super(stub, KtStubElementTypes.SCRIPT)

    override fun getFqName(): FqName = stub?.getFqName()
        ?: containingKtFile.packageFqName.child(kotlinScriptDefinition.getScriptName(this))

    override fun getName(): String? = fqName.shortName().asString()

    override fun getDeclarations(): List<KtDeclaration> = listOf(body)

    override fun <R, D> accept(visitor: KtVisitor<R, D>, data: D): R = visitor.visitScript(this, data)

    companion object {

        fun getParentScript(declaration: KtDeclaration, precalculatedParent: KtDeclaration? = null /* optimization */): KtScript? {
            val parent = precalculatedParent ?: KtStubbedPsiUtil.getContainingDeclaration(declaration)
            return parent?.let { KtStubbedPsiUtil.getContainingDeclaration(it) } as? KtScript
        }
    }
}
