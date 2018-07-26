/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifiableCodeBlock
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.stubs.KotlinPlaceHolderStub
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes
import org.jetbrains.kotlin.psi.typeRefHelpers.getTypeReference
import org.jetbrains.kotlin.psi.typeRefHelpers.setTypeReference

const val SCRIPT_BODY_METHOD_NAME = "<script body>"

class KtScriptBody : KtDeclarationStub<KotlinPlaceHolderStub<KtScriptBody>>,
    KtFunction, PsiModifiableCodeBlock, KtDeclaration
{
    constructor(node: ASTNode) : super(node)

    constructor(stub: KotlinPlaceHolderStub<KtScriptBody>) : super(stub, KtStubElementTypes.SCRIPT_BODY)

    override fun <R, D> accept(visitor: KtVisitor<R, D>, data: D): R = visitor.visitScriptBody(this, data)

    override fun getTypeParameters() = emptyList<KtTypeParameter>()

    override fun getBodyExpression(): KtExpression? = findNotNullChildByClass(KtBlockExpression::class.java)

    override fun hasDeclaredReturnType(): Boolean = false

    override fun getValueParameterList(): KtParameterList? = null

    override fun setTypeReference(typeRef: KtTypeReference?): KtTypeReference? {
        return setTypeReference(this, valueParameterList, typeRef)
    }

    override fun getColon(): PsiElement? = null

    override fun getTypeParameterList(): KtTypeParameterList? = null

    override fun getTypeConstraintList(): KtTypeConstraintList? = null

    override fun getTypeReference(): KtTypeReference? {
        val stub = stub
        if (stub != null) {
            val typeReferences = getStubOrPsiChildrenAsList(KtStubElementTypes.TYPE_REFERENCE)
            val returnTypeIndex = 1
            return if (returnTypeIndex >= typeReferences.size) {
                null
            } else typeReferences[returnTypeIndex]
        }
        return getTypeReference(this)
    }

    override fun getEqualsToken(): PsiElement? = null

    override fun getTypeConstraints() = emptyList<KtTypeConstraint>()

    override fun getNameIdentifier(): PsiElement? = null

    override fun getName(): String? = SCRIPT_BODY_METHOD_NAME

    override fun getNameAsSafeName() = Name.special(SCRIPT_BODY_METHOD_NAME)

    override fun getFqName(): FqName? = null

    override fun getNameAsName() = nameAsSafeName

    override fun hasBlockBody(): Boolean = true

    override fun hasBody(): Boolean = true

    override fun getValueParameters() = emptyList<KtParameter>()

    override fun getReceiverTypeReference(): KtTypeReference? = null

    override fun setName(name: String): PsiElement {
        return this
    }

    override fun isLocal(): Boolean = false

    override fun shouldChangeModificationCount(place: PsiElement?): Boolean = false
}