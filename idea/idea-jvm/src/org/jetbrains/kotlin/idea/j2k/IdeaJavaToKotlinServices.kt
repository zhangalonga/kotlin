/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.j2k

import com.intellij.codeInspection.dataFlow.DfaUtil
import com.intellij.codeInspection.dataFlow.Nullness
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.j2k.ast.Nullability

object IdeaJavaToKotlinServices : JavaToKotlinConverterServices {
    override val referenceSearcher: ReferenceSearcher
        get() = IdeaReferenceSearcher

    override val superMethodsSearcher: SuperMethodsSearcher
        get() = IdeaSuperMethodSearcher

    override val resolverForConverter: ResolverForConverter
        get() = IdeaResolverForConverter

    override val docCommentConverter: DocCommentConverter
        get() = IdeaDocCommentConverter

    override val javaDataFlowAnalyzerFacade: JavaDataFlowAnalyzerFacade
        get() = IdeaJavaDataFlowAnalyzerFacade
}

object IdeaSuperMethodSearcher : SuperMethodsSearcher {
    override fun findDeepestSuperMethods(method: PsiMethod) = method.findDeepestSuperMethods().asList()
}

private object IdeaJavaDataFlowAnalyzerFacade : JavaDataFlowAnalyzerFacade {
    override fun variableNullability(variable: PsiVariable, context: PsiElement): Nullability =
            DfaUtil.checkNullness(variable, context).toNullability()

    override fun methodNullability(method: PsiMethod): Nullability =
            DfaUtil.inferMethodNullity(method).toNullability()

    private fun Nullness.toNullability() = when (this) {
        Nullness.UNKNOWN -> Nullability.Default
        Nullness.NOT_NULL -> Nullability.NotNull
        Nullness.NULLABLE -> Nullability.Nullable
    }
}