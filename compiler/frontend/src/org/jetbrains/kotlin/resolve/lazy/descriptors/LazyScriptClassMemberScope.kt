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

package org.jetbrains.kotlin.resolve.lazy.descriptors

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ClassConstructorDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.SCRIPT_BODY_METHOD_NAME
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.lazy.declarations.ClassMemberDeclarationProvider
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection

class LazyScriptClassMemberScope(
    resolveSession: ResolveSession,
    declarationProvider: ClassMemberDeclarationProvider,
    private val scriptDescriptor: LazyScriptDescriptor,
    trace: BindingTrace
) : LazyClassMemberScope(resolveSession, declarationProvider, scriptDescriptor, trace) {

    private val scriptPrimaryConstructor: () -> ClassConstructorDescriptorImpl? = resolveSession.storageManager.createNullableLazyValue {
        val baseClass = scriptDescriptor.baseClassDescriptor()
        val baseConstructorDescriptor = baseClass?.unsubstitutedPrimaryConstructor
        if (baseConstructorDescriptor != null) {
            val builtIns = scriptDescriptor.builtIns
            val implicitReceiversParamType =
                if (scriptDescriptor.scriptDefinition().implicitReceivers.isEmpty()) null
                else {
                    "implicitReceivers" to builtIns.array.substitute(builtIns.anyType)!!
                }
            val environmentVarsParamType =
                if (scriptDescriptor.scriptDefinition().environmentVariables.isEmpty()) null
                else {
                    "environmentVariables" to builtIns.map.substitute(builtIns.stringType, builtIns.nullableAnyType)!!
                }
            val annotations = baseConstructorDescriptor.annotations
            val constructorDescriptor = ClassConstructorDescriptorImpl.create(
                scriptDescriptor, annotations, baseConstructorDescriptor.isPrimary, scriptDescriptor.source
            )
            var paramsIndexBase = baseConstructorDescriptor.valueParameters.lastIndex + 1
            val syntheticParameters =
                listOf(implicitReceiversParamType, environmentVarsParamType).mapNotNull { param: Pair<String, KotlinType>? ->
                    if (param == null) null
                    else ValueParameterDescriptorImpl(
                        constructorDescriptor,
                        null,
                        paramsIndexBase++,
                        Annotations.EMPTY,
                        Name.identifier(param.first),
                        param.second,
                        false, false, false, null, SourceElement.NO_SOURCE
                    )
                }
            val parameters = baseConstructorDescriptor.valueParameters.map { it.copy(constructorDescriptor, it.name, it.index) } +
                    syntheticParameters
            constructorDescriptor.initialize(parameters, baseConstructorDescriptor.visibility)
            constructorDescriptor.returnType = scriptDescriptor.defaultType
            constructorDescriptor
        } else {
            null
        }
    }

    private val scriptBodyFunction: () -> SimpleFunctionDescriptor? = resolveSession.storageManager.createNullableLazyValue {
        val bodyDescr = SimpleFunctionDescriptorImpl.create(
            scriptDescriptor, Annotations.EMPTY, Name.special("<scriptBody>"), CallableMemberDescriptor.Kind.DECLARATION,
            scriptDescriptor.source
        )
        bodyDescr.initialize(
            null, null, // receiver
            emptyList(), // type params
            emptyList(), // value params - TODO
            scriptDescriptor.builtIns.array.defaultType, // return type
            Modality.FINAL,
            Visibilities.PRIVATE
        )
        resolveSession.trace.record(BindingContext.FUNCTION, scriptDescriptor.scriptInfo.script.body, bodyDescr)
        bodyDescr
    }

    override fun resolvePrimaryConstructor(): ClassConstructorDescriptor? {
        val constructor = scriptPrimaryConstructor()
                ?: ClassConstructorDescriptorImpl.create(
                    scriptDescriptor,
                    Annotations.EMPTY,
                    true,
                    SourceElement.NO_SOURCE
                ).initialize(
                    emptyList(),
                    Visibilities.PUBLIC
                )
        setDeferredReturnType(constructor)
        return constructor
    }

    override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<SimpleFunctionDescriptor> {
//        val bodyFn = if (name.isSpecial && name.asString() == SCRIPT_BODY_METHOD_NAME) scriptBodyFunction() else null
        val baseFns = super.getContributedFunctions(name, location)
        return baseFns
//        return if (bodyFn == null) baseFns else baseFns + bodyFn
    }

    override fun createPropertiesFromPrimaryConstructorParameters(name: Name, result: MutableSet<PropertyDescriptor>) {
    }
}

private fun ClassDescriptor.substitute(vararg types: KotlinType): KotlinType? =
    KotlinTypeFactory.simpleType(this.defaultType, arguments = types.map { it.asTypeProjection() })
