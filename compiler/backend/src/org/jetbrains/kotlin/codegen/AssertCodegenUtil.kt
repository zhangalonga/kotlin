/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen

import org.jetbrains.kotlin.codegen.coroutines.createCustomCopy
import org.jetbrains.kotlin.config.JVMAssertionsMode
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.DelegatingBindingTrace
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.calls.tasks.TracingStrategy
import org.jetbrains.kotlin.resolve.descriptorUtil.module

object AssertCodegenUtil {
    private lateinit var assertFunctionDescriptor: Collection<FunctionDescriptor>

    private fun getAssertFunctionDescriptors(descriptor: CallableDescriptor): Collection<FunctionDescriptor> {
        if (!this::assertFunctionDescriptor.isInitialized) {
            assertFunctionDescriptor = descriptor.module.getPackage(FqName("kotlin")).memberScope
                .getContributedFunctions(Name.identifier("assert"), NoLookupLocation.FROM_BACKEND)
        }
        return assertFunctionDescriptor
    }

    @JvmStatic
    fun isAssertCall(resolvedCall: ResolvedCall<*>): Boolean {
        return resolvedCall.resultingDescriptor in getAssertFunctionDescriptors(resolvedCall.resultingDescriptor)
    }

    @JvmStatic
    fun generateAssert(
        assertionsMode: JVMAssertionsMode,
        resolvedCall: ResolvedCall<*>,
        codegen: ExpressionCodegen,
        parentCodegen: MemberCodegen<*>
    ) {
        assert(isAssertCall(resolvedCall)) { "generateAssert expects call of kotlin.assert function" }
        when (assertionsMode) {
            JVMAssertionsMode.ALWAYS_ENABLE -> generateEnabledAssert(resolvedCall, codegen)
            JVMAssertionsMode.ALWAYS_DISABLE -> {
                // Nothing to do: assertions disabled
            }
            JVMAssertionsMode.JVM -> generateJvmAssert(resolvedCall, codegen, parentCodegen)
            else -> error("legacy assertions mode shall be handled in ExpressionCodegen")
        }
    }

    private fun generateJvmAssert(resolvedCall: ResolvedCall<*>, codegen: ExpressionCodegen, parentCodegen: MemberCodegen<*>) {
        TODO()
//        val (conditionArgument, lambdaArgument) = getAssertArguments(resolvedCall)
//        val condition = putConditionOnTop(conditionArgument, codegen)
//        val elseLabel = Label()
//        BranchedValue.condJump(condition, elseLabel, true, codegen.v)
//        generateThrowAssertionError(lambdaArgument, codegen)
//        codegen.v.mark(elseLabel)
    }

    @Suppress("UNCHECKED_CAST")
    private fun generateEnabledAssert(resolvedCall: ResolvedCall<*>, codegen: ExpressionCodegen) {
        val replaced = (resolvedCall as ResolvedCall<FunctionDescriptor>).replaceAssertWithAssertInner()
        codegen.invokeMethodWithArguments(
            codegen.typeMapper.mapToCallableMethod(replaced.resultingDescriptor, false),
            replaced,
            StackValue.none()
        )
    }
}

private fun <D : FunctionDescriptor> ResolvedCall<D>.replaceAssertWithAssertInner(): ResolvedCall<D> {
    val newCandidateDescriptor = resultingDescriptor.createCustomCopy {
        setName(Name.identifier("assertInner"))
    }
    val newResolvedCall = ResolvedCallImpl(
        call,
        newCandidateDescriptor,
        dispatchReceiver, extensionReceiver, explicitReceiverKind,
        null, DelegatingBindingTrace(BindingTraceContext().bindingContext, "Temporary trace for assertInner"),
        TracingStrategy.EMPTY, MutableDataFlowInfoForArguments.WithoutArgumentsCheck(DataFlowInfo.EMPTY)
    )
    valueArguments.forEach {
        newResolvedCall.recordValueArgument(newCandidateDescriptor.valueParameters[it.key.index], it.value)
    }
    return newResolvedCall
}
