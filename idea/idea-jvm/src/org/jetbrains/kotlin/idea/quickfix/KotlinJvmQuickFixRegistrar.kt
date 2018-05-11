/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors.*
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.*
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateLocalVariableActionFactory
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterByNamedArgumentActionFactory
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterByRefActionFactory

class KotlinJvmQuickFixRegistrar : QuickFixContributor {
    override fun registerQuickFixes(quickFixes: QuickFixes) {
        fun DiagnosticFactory<*>.registerFactory(vararg factory: KotlinIntentionActionsFactory) {
            quickFixes.register(this, *factory)
        }

        fun DiagnosticFactory<*>.registerActions(vararg action: IntentionAction) {
            quickFixes.register(this, *action)
        }

        MUST_BE_INITIALIZED_OR_BE_ABSTRACT.registerFactory(InitializePropertyQuickFixFactory)
        MUST_BE_INITIALIZED.registerFactory(InitializePropertyQuickFixFactory)

        val changeFunctionReturnTypeFix = ChangeCallableReturnTypeFix.ChangingReturnTypeToUnitFactory
        RETURN_TYPE_MISMATCH.registerFactory(changeFunctionReturnTypeFix)
        NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY.registerFactory(changeFunctionReturnTypeFix)
        RETURN_TYPE_MISMATCH_ON_OVERRIDE.registerFactory(ChangeCallableReturnTypeFix.ReturnTypeMismatchOnOverrideFactory)
        COMPONENT_FUNCTION_RETURN_TYPE_MISMATCH.registerFactory(ChangeCallableReturnTypeFix.ComponentFunctionReturnTypeMismatchFactory)
        HAS_NEXT_FUNCTION_TYPE_MISMATCH.registerFactory(ChangeCallableReturnTypeFix.HasNextFunctionTypeMismatchFactory)
        COMPARE_TO_TYPE_MISMATCH.registerFactory(ChangeCallableReturnTypeFix.CompareToTypeMismatchFactory)
        IMPLICIT_NOTHING_RETURN_TYPE.registerFactory(ChangeCallableReturnTypeFix.ChangingReturnTypeToNothingFactory)

        TOO_MANY_ARGUMENTS.registerFactory(ChangeFunctionSignatureFix)
        NO_VALUE_FOR_PARAMETER.registerFactory(ChangeFunctionSignatureFix)
        UNUSED_PARAMETER.registerFactory(RemoveUnusedFunctionParameterFix)
        EXPECTED_PARAMETERS_NUMBER_MISMATCH.registerFactory(ChangeFunctionLiteralSignatureFix)

        EXPECTED_TYPE_MISMATCH.registerFactory(ChangeFunctionLiteralReturnTypeFix)
        ASSIGNMENT_TYPE_MISMATCH.registerFactory(ChangeFunctionLiteralReturnTypeFix)

        UNRESOLVED_REFERENCE.registerFactory(CreateUnaryOperationActionFactory)
        UNRESOLVED_REFERENCE_WRONG_RECEIVER.registerFactory(CreateUnaryOperationActionFactory)
        NO_VALUE_FOR_PARAMETER.registerFactory(CreateUnaryOperationActionFactory)

        UNRESOLVED_REFERENCE_WRONG_RECEIVER.registerFactory(CreateBinaryOperationActionFactory)
        UNRESOLVED_REFERENCE.registerFactory(CreateBinaryOperationActionFactory)
        NONE_APPLICABLE.registerFactory(CreateBinaryOperationActionFactory)
        NO_VALUE_FOR_PARAMETER.registerFactory(CreateBinaryOperationActionFactory)
        TOO_MANY_ARGUMENTS.registerFactory(CreateBinaryOperationActionFactory)
        TYPE_MISMATCH_ERRORS.forEach { it.registerFactory(CreateBinaryOperationActionFactory) }

        UNRESOLVED_REFERENCE_WRONG_RECEIVER.registerFactory(*CreateCallableFromCallActionFactory.INSTANCES)
        UNRESOLVED_REFERENCE.registerFactory(*CreateCallableFromCallActionFactory.INSTANCES)
        UNRESOLVED_REFERENCE.registerFactory(CreateFunctionFromCallableReferenceActionFactory)
        NO_VALUE_FOR_PARAMETER.registerFactory(*CreateCallableFromCallActionFactory.INSTANCES)
        TOO_MANY_ARGUMENTS.registerFactory(*CreateCallableFromCallActionFactory.INSTANCES)
        EXPRESSION_EXPECTED_PACKAGE_FOUND.registerFactory(*CreateCallableFromCallActionFactory.INSTANCES)
        NONE_APPLICABLE.registerFactory(*CreateCallableFromCallActionFactory.INSTANCES)
        TYPE_MISMATCH.registerFactory(*CreateCallableFromCallActionFactory.FUNCTIONS)

        NO_VALUE_FOR_PARAMETER.registerFactory(CreateConstructorFromDelegationCallActionFactory)
        TOO_MANY_ARGUMENTS.registerFactory(CreateConstructorFromDelegationCallActionFactory)

        NO_VALUE_FOR_PARAMETER.registerFactory(CreateConstructorFromSuperTypeCallActionFactory)
        TOO_MANY_ARGUMENTS.registerFactory(CreateConstructorFromSuperTypeCallActionFactory)

        UNRESOLVED_REFERENCE.registerFactory(CreateLocalVariableActionFactory)
        EXPRESSION_EXPECTED_PACKAGE_FOUND.registerFactory(CreateLocalVariableActionFactory)

        UNRESOLVED_REFERENCE.registerFactory(CreateParameterByRefActionFactory)
        UNRESOLVED_REFERENCE_WRONG_RECEIVER.registerFactory(CreateParameterByRefActionFactory)
        EXPRESSION_EXPECTED_PACKAGE_FOUND.registerFactory(CreateParameterByRefActionFactory)

        NAMED_PARAMETER_NOT_FOUND.registerFactory(CreateParameterByNamedArgumentActionFactory)

        FUNCTION_EXPECTED.registerFactory(CreateInvokeFunctionActionFactory)

        val factoryForTypeMismatchError = QuickFixFactoryForTypeMismatchError()
        TYPE_MISMATCH.registerFactory(factoryForTypeMismatchError)
        NULL_FOR_NONNULL_TYPE.registerFactory(factoryForTypeMismatchError)
        CONSTANT_EXPECTED_TYPE_MISMATCH.registerFactory(factoryForTypeMismatchError)
        TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH.registerFactory(factoryForTypeMismatchError)

        NO_GET_METHOD.registerFactory(CreateGetFunctionActionFactory)
        TYPE_MISMATCH_ERRORS.forEach { it.registerFactory(CreateGetFunctionActionFactory) }
        NO_SET_METHOD.registerFactory(CreateSetFunctionActionFactory)
        TYPE_MISMATCH_ERRORS.forEach { it.registerFactory(CreateSetFunctionActionFactory) }
        HAS_NEXT_MISSING.registerFactory(CreateHasNextFunctionActionFactory)
        HAS_NEXT_FUNCTION_NONE_APPLICABLE.registerFactory(CreateHasNextFunctionActionFactory)
        NEXT_MISSING.registerFactory(CreateNextFunctionActionFactory)
        NEXT_NONE_APPLICABLE.registerFactory(CreateNextFunctionActionFactory)
        ITERATOR_MISSING.registerFactory(CreateIteratorFunctionActionFactory)
        ITERATOR_ON_NULLABLE.registerFactory(MissingIteratorExclExclFixFactory)
        COMPONENT_FUNCTION_MISSING.registerFactory(
            CreateComponentFunctionActionFactory,
            CreateDataClassPropertyFromDestructuringActionFactory
        )

        DELEGATE_SPECIAL_FUNCTION_MISSING.registerFactory(CreatePropertyDelegateAccessorsActionFactory)
        DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE.registerFactory(CreatePropertyDelegateAccessorsActionFactory)
    }
}
