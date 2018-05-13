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

package org.jetbrains.kotlin.cfg
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.cfg.pseudocode.PseudoValue
import org.jetbrains.kotlin.cfg.pseudocode.Pseudocode
import org.jetbrains.kotlin.cfg.pseudocode.PseudocodeUtil
import org.jetbrains.kotlin.cfg.pseudocode.instructions.Instruction
import org.jetbrains.kotlin.cfg.pseudocode.instructions.eval.*
import org.jetbrains.kotlin.cfg.pseudocode.instructions.special.VariableDeclarationInstruction
import org.jetbrains.kotlin.cfg.pseudocodeTraverser.Edges
import org.jetbrains.kotlin.cfg.pseudocodeTraverser.TraversalOrder
import org.jetbrains.kotlin.cfg.pseudocodeTraverser.traverse
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingContextUtils.variableDescriptorForDeclaration
import org.jetbrains.kotlin.resolve.calls.checkers.isOperatorMod
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

private typealias ImmutableSet<T> = javaslang.collection.Set<T>
private typealias ImmutableHashSet<T> = javaslang.collection.HashSet<T>

class PseudocodeVariablesData(val pseudocode: Pseudocode, private val bindingContext: BindingContext) {

    private val pseudoValueToValue = hashMapOf<PseudoValue, VariableWithConstValue>()
    private val containsDoWhile = pseudocode.rootPseudocode.containsDoWhile
    private val pseudocodeVariableDataCollector = PseudocodeVariableDataCollector(bindingContext, pseudocode)
    private class VariablesForDeclaration(
        val valsWithTrivialInitializer: Set<VariableDescriptor>,
        val nonTrivialVariables: Set<VariableDescriptor>
    ) {
        val allVars =
            if (nonTrivialVariables.isEmpty())
                valsWithTrivialInitializer
            else
                LinkedHashSet(valsWithTrivialInitializer).also { it.addAll(nonTrivialVariables) }
    }
    private val declaredVariablesForDeclaration = hashMapOf<Pseudocode, VariablesForDeclaration>()
    private val rootVariables by lazy(LazyThreadSafetyMode.NONE) {
        getAllDeclaredVariables(pseudocode, includeInsideLocalDeclarations = true)
    }

    val variableInitializers: Map<Instruction, Edges<ReadOnlyInitControlFlowInfo>> by lazy {
        computeVariableInitializers()
    }

    val variableValues: Map<Instruction, Edges<ReadOnlyConstValueControlFlowInfo>> by lazy {
        computeConstValues()
    }

    val blockScopeVariableInfo: BlockScopeVariableInfo
        get() = pseudocodeVariableDataCollector.blockScopeVariableInfo

    fun getDeclaredVariables(pseudocode: Pseudocode, includeInsideLocalDeclarations: Boolean): Set<VariableDescriptor> =
        getAllDeclaredVariables(pseudocode, includeInsideLocalDeclarations).allVars

    fun isVariableWithTrivialInitializer(variableDescriptor: VariableDescriptor) =
        variableDescriptor in rootVariables.valsWithTrivialInitializer

    private fun getAllDeclaredVariables(pseudocode: Pseudocode, includeInsideLocalDeclarations: Boolean): VariablesForDeclaration {
        if (!includeInsideLocalDeclarations) {
            return getUpperLevelDeclaredVariables(pseudocode)
        }
        val nonTrivialVariables = linkedSetOf<VariableDescriptor>()
        val valsWithTrivialInitializer = linkedSetOf<VariableDescriptor>()
        addVariablesFromPseudocode(pseudocode, nonTrivialVariables, valsWithTrivialInitializer)

        for (localFunctionDeclarationInstruction in pseudocode.localDeclarations) {
            val localPseudocode = localFunctionDeclarationInstruction.body
            addVariablesFromPseudocode(localPseudocode, nonTrivialVariables, valsWithTrivialInitializer)
        }
        return VariablesForDeclaration(valsWithTrivialInitializer, nonTrivialVariables)
    }

    private fun addVariablesFromPseudocode(
        pseudocode: Pseudocode,
        nonTrivialVariables: MutableSet<VariableDescriptor>,
        valsWithTrivialInitializer: MutableSet<VariableDescriptor>
    ) {
        getUpperLevelDeclaredVariables(pseudocode).let {
            nonTrivialVariables.addAll(it.nonTrivialVariables)
            valsWithTrivialInitializer.addAll(it.valsWithTrivialInitializer)
        }
    }

    private fun getUpperLevelDeclaredVariables(pseudocode: Pseudocode) = declaredVariablesForDeclaration.getOrPut(pseudocode) {
        computeDeclaredVariablesForPseudocode(pseudocode)
    }

    private fun computeDeclaredVariablesForPseudocode(pseudocode: Pseudocode): VariablesForDeclaration {
        val valsWithTrivialInitializer = linkedSetOf<VariableDescriptor>()
        val nonTrivialVariables = linkedSetOf<VariableDescriptor>()
        for (instruction in pseudocode.instructions) {
            if (instruction is VariableDeclarationInstruction) {
                val variableDeclarationElement = instruction.variableDeclarationElement
                val descriptor =
                    variableDescriptorForDeclaration(
                        bindingContext.get(BindingContext.DECLARATION_TO_DESCRIPTOR, variableDeclarationElement)
                    ) ?: continue

                if (!containsDoWhile && isValWithTrivialInitializer(variableDeclarationElement, descriptor)) {
                    valsWithTrivialInitializer.add(descriptor)
                } else {
                    nonTrivialVariables.add(descriptor)
                }
            }
        }

        return VariablesForDeclaration(valsWithTrivialInitializer, nonTrivialVariables)
    }
    private fun isValWithTrivialInitializer(variableDeclarationElement: KtDeclaration, descriptor: VariableDescriptor) =
        variableDeclarationElement is KtParameter || variableDeclarationElement is KtObjectDeclaration ||
                variableDeclarationElement.safeAs<KtVariableDeclaration>()?.isVariableWithTrivialInitializer(descriptor) == true

    private fun KtVariableDeclaration.isVariableWithTrivialInitializer(descriptor: VariableDescriptor): Boolean {
        if (descriptor.isPropertyWithoutBackingField()) return true
        if (isVar) return false
        return initializer != null || safeAs<KtProperty>()?.delegate != null || this is KtDestructuringDeclarationEntry
    }

    private fun VariableDescriptor.isPropertyWithoutBackingField(): Boolean {
        if (this !is PropertyDescriptor) return false
        return bindingContext.get(BindingContext.BACKING_FIELD_REQUIRED, this) != true
    }

    // variable initializers

    private fun computeVariableInitializers(): Map<Instruction, Edges<ReadOnlyInitControlFlowInfo>> {

        val blockScopeVariableInfo = pseudocodeVariableDataCollector.blockScopeVariableInfo

        val resultForValsWithTrivialInitializer = computeInitInfoForTrivialVals()

        if (rootVariables.nonTrivialVariables.isEmpty()) return resultForValsWithTrivialInitializer

        return pseudocodeVariableDataCollector.collectData(
            TraversalOrder.FORWARD,
            InitControlFlowInfo()
        ) { instruction: Instruction, incomingEdgesData: Collection<InitControlFlowInfo> ->

            val enterInstructionData = mergeIncomingEdgesDataForInitializers(instruction, incomingEdgesData, blockScopeVariableInfo)
            val exitInstructionData = addVariableInitStateFromCurrentInstructionIfAny(
                instruction, enterInstructionData, blockScopeVariableInfo
            )
            Edges(enterInstructionData, exitInstructionData)
        }.mapValues { (instruction, edges) ->
                val trivialEdges = resultForValsWithTrivialInitializer[instruction]!!
                Edges(trivialEdges.incoming.replaceDelegate(edges.incoming), trivialEdges.outgoing.replaceDelegate(edges.outgoing))
            }
    }

    private fun computeInitInfoForTrivialVals(): Map<Instruction, Edges<ReadOnlyInitControlFlowInfoImpl>> {
        val result = hashMapOf<Instruction, Edges<ReadOnlyInitControlFlowInfoImpl>>()
        var declaredSet = ImmutableHashSet.empty<VariableDescriptor>()
        var initSet = ImmutableHashSet.empty<VariableDescriptor>()
        pseudocode.traverse(TraversalOrder.FORWARD) { instruction ->
            val enterState = ReadOnlyInitControlFlowInfoImpl(declaredSet, initSet, null)
            when (instruction) {
                is VariableDeclarationInstruction ->
                    extractValWithTrivialInitializer(instruction)?.let { variableDescriptor ->
                        declaredSet = declaredSet.add(variableDescriptor)
                    }
                is WriteValueInstruction -> {
                    val variableDescriptor = extractValWithTrivialInitializer(instruction)
                    if (variableDescriptor != null && instruction.isTrivialInitializer()) {
                        initSet = initSet.add(variableDescriptor)
                    }
                }
            }

            val afterState = ReadOnlyInitControlFlowInfoImpl(declaredSet, initSet, null)

            result[instruction] = Edges(enterState, afterState)
        }
        return result
    }

    private fun WriteValueInstruction.isTrivialInitializer() =
            // WriteValueInstruction having KtDeclaration as an element means
            // it must be a write happened at the same time when
            // the variable (common variable/parameter/object) has been declared
        element is KtDeclaration

    private inner class ReadOnlyInitControlFlowInfoImpl(
        val declaredSet: ImmutableSet<VariableDescriptor>,
        val initSet: ImmutableSet<VariableDescriptor>,
        private val delegate: ReadOnlyInitControlFlowInfo?
    ) : ReadOnlyInitControlFlowInfo {
        override fun getOrNull(variableDescriptor: VariableDescriptor): VariableControlFlowState? {
            if (variableDescriptor in declaredSet) {
                return VariableControlFlowState.create(isInitialized = variableDescriptor in initSet, isDeclared = true)
            }
            return delegate?.getOrNull(variableDescriptor)
        }

        override fun checkDefiniteInitializationInWhen(merge: ReadOnlyInitControlFlowInfo): Boolean =
            delegate?.checkDefiniteInitializationInWhen(merge) ?: false

        fun replaceDelegate(newDelegate: ReadOnlyInitControlFlowInfo): ReadOnlyInitControlFlowInfo =
            ReadOnlyInitControlFlowInfoImpl(declaredSet, initSet, newDelegate)

        override fun asMap(): ImmutableMap<VariableDescriptor, VariableControlFlowState> {
            val initial = delegate?.asMap() ?: ImmutableHashMap.empty()

            return declaredSet.fold(initial) { acc, variableDescriptor ->
                acc.put(variableDescriptor, getOrNull(variableDescriptor)!!)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ReadOnlyInitControlFlowInfoImpl

            if (declaredSet != other.declaredSet) return false
            if (initSet != other.initSet) return false
            if (delegate != other.delegate) return false

            return true
        }

        override fun hashCode(): Int {
            var result = declaredSet.hashCode()
            result = 31 * result + initSet.hashCode()
            result = 31 * result + (delegate?.hashCode() ?: 0)
            return result
        }
    }

    private fun addVariableInitStateFromCurrentInstructionIfAny(
        instruction: Instruction,
        enterInstructionData: InitControlFlowInfo,
        blockScopeVariableInfo: BlockScopeVariableInfo
    ): InitControlFlowInfo {
        if (instruction is MagicInstruction) {
            if (instruction.kind === MagicKind.EXHAUSTIVE_WHEN_ELSE) {
                return enterInstructionData.iterator().fold(enterInstructionData) { result, (key, value) ->
                    if (!value.definitelyInitialized()) {
                        result.put(key, VariableControlFlowState.createInitializedExhaustively(value.isDeclared))
                    } else result
                }
            }
        }
        if (instruction !is WriteValueInstruction && instruction !is VariableDeclarationInstruction) {
            return enterInstructionData
        }
        val variable =
            PseudocodeUtil.extractVariableDescriptorIfAny(instruction, bindingContext)
                ?.takeIf { it in rootVariables.nonTrivialVariables }
                    ?: return enterInstructionData
        var exitInstructionData = enterInstructionData
        if (instruction is WriteValueInstruction) {
            // if writing to already initialized object
            if (!PseudocodeUtil.isThisOrNoDispatchReceiver(instruction, bindingContext)) {
                return enterInstructionData
            }

            val enterInitState = enterInstructionData.getOrNull(variable)
            val initializationAtThisElement = VariableControlFlowState.create(instruction.element is KtProperty, enterInitState)
            exitInstructionData = exitInstructionData.put(variable, initializationAtThisElement, enterInitState)
        } else {
            // instruction instanceof VariableDeclarationInstruction
            val enterInitState =
                enterInstructionData.getOrNull(variable)
                        ?: getDefaultValueForInitializers(variable, instruction, blockScopeVariableInfo)

            if (!enterInitState.mayBeInitialized() || !enterInitState.isDeclared) {
                val variableDeclarationInfo = VariableControlFlowState.create(enterInitState.initState, isDeclared = true)
                exitInstructionData = exitInstructionData.put(variable, variableDeclarationInfo, enterInitState)
            }
        }
        return exitInstructionData
    }

    // variable use

    val variableUseStatusData: Map<Instruction, Edges<ReadOnlyUseControlFlowInfo>>
        get() {
            val edgesForTrivialVals = computeUseInfoForTrivialVals()
            if (rootVariables.nonTrivialVariables.isEmpty()) {
                return hashMapOf<Instruction, Edges<ReadOnlyUseControlFlowInfo>>().apply {
                    pseudocode.traverse(TraversalOrder.FORWARD) { instruction ->
                        put(instruction, edgesForTrivialVals)
                    }
                }
            }

            return pseudocodeVariableDataCollector.collectData(
                TraversalOrder.BACKWARD,
                UseControlFlowInfo()
            ) { instruction: Instruction, incomingEdgesData: Collection<UseControlFlowInfo> ->

                val enterResult: UseControlFlowInfo = if (incomingEdgesData.size == 1) {
                    incomingEdgesData.single()
                } else {
                    incomingEdgesData.fold(UseControlFlowInfo()) { result, edgeData ->
                        edgeData.iterator().fold(result) { subResult, (variableDescriptor, variableUseState) ->
                            subResult.put(variableDescriptor, variableUseState.merge(subResult.getOrNull(variableDescriptor)))
                        }
                    }
                }

                val variableDescriptor =
                    PseudocodeUtil.extractVariableDescriptorFromReference(instruction, bindingContext)
                        ?.takeIf { it in rootVariables.nonTrivialVariables }
                if (variableDescriptor == null || instruction !is ReadValueInstruction && instruction !is WriteValueInstruction) {
                    Edges(enterResult, enterResult)
                } else {
                    val exitResult =
                        if (instruction is ReadValueInstruction) {
                            enterResult.put(variableDescriptor, VariableUseState.READ)
                        } else {
                            var variableUseState: VariableUseState? = enterResult.getOrNull(variableDescriptor)
                            if (variableUseState == null) {
                                variableUseState = VariableUseState.UNUSED
                            }
                            when (variableUseState) {
                                VariableUseState.UNUSED, VariableUseState.ONLY_WRITTEN_NEVER_READ ->
                                    enterResult.put(variableDescriptor, VariableUseState.ONLY_WRITTEN_NEVER_READ)
                                VariableUseState.WRITTEN_AFTER_READ, VariableUseState.READ ->
                                    enterResult.put(variableDescriptor, VariableUseState.WRITTEN_AFTER_READ)
                            }
                        }
                    Edges(enterResult, exitResult)
                }
            }.mapValues { (_, edges) ->

                    Edges(
                        edgesForTrivialVals.incoming.replaceDelegate(edges.incoming),
                        edgesForTrivialVals.outgoing.replaceDelegate(edges.outgoing)
                    )
                }
        }

    private fun computeUseInfoForTrivialVals(): Edges<ReadOnlyUseControlFlowInfoImpl> {
        val used = hashSetOf<VariableDescriptor>()

        pseudocode.traverse(TraversalOrder.FORWARD) { instruction ->
            if (instruction is ReadValueInstruction) {
                extractValWithTrivialInitializer(instruction)?.let {
                    used.add(it)
                }
            }
        }

        val constantUseInfo = ReadOnlyUseControlFlowInfoImpl(used, null)
        return Edges(constantUseInfo, constantUseInfo)
    }

    private fun extractValWithTrivialInitializer(instruction: Instruction): VariableDescriptor? {
        return PseudocodeUtil.extractVariableDescriptorIfAny(instruction, bindingContext)?.takeIf {
            it in rootVariables.valsWithTrivialInitializer
        }
    }

    private inner class ReadOnlyUseControlFlowInfoImpl(
        val used: Set<VariableDescriptor>,
        val delegate: ReadOnlyUseControlFlowInfo?
    ) : ReadOnlyUseControlFlowInfo {
        override fun getOrNull(variableDescriptor: VariableDescriptor): VariableUseState? {
            if (variableDescriptor in used) return VariableUseState.READ
            return delegate?.getOrNull(variableDescriptor)
        }

        fun replaceDelegate(newDelegate: ReadOnlyUseControlFlowInfo): ReadOnlyUseControlFlowInfo =
            ReadOnlyUseControlFlowInfoImpl(used, newDelegate)

        override fun asMap(): ImmutableMap<VariableDescriptor, VariableUseState> {
            val initial = delegate?.asMap() ?: ImmutableHashMap.empty()

            return used.fold(initial) { acc, variableDescriptor ->
                acc.put(variableDescriptor, getOrNull(variableDescriptor)!!)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ReadOnlyUseControlFlowInfoImpl

            if (used != other.used) return false
            if (delegate != other.delegate) return false

            return true
        }

        override fun hashCode(): Int {
            var result = used.hashCode()
            result = 31 * result + (delegate?.hashCode() ?: 0)
            return result
        }

    }

    companion object {

        @JvmStatic
        fun getDefaultValueForInitializers(
            variable: VariableDescriptor,
            instruction: Instruction,
            blockScopeVariableInfo: BlockScopeVariableInfo
        ): VariableControlFlowState {
            //todo: think of replacing it with "MapWithDefaultValue"
            val declaredIn = blockScopeVariableInfo.declaredIn[variable]
            val declaredOutsideThisDeclaration =
                declaredIn == null //declared outside this pseudocode
                        || declaredIn.blockScopeForContainingDeclaration != instruction.blockScope.blockScopeForContainingDeclaration
            return VariableControlFlowState.create(isInitialized = declaredOutsideThisDeclaration)
        }

        private val EMPTY_INIT_CONTROL_FLOW_INFO = InitControlFlowInfo()

        private fun mergeIncomingEdgesDataForInitializers(
            instruction: Instruction,
            incomingEdgesData: Collection<InitControlFlowInfo>,
            blockScopeVariableInfo: BlockScopeVariableInfo
        ): InitControlFlowInfo {
            if (incomingEdgesData.size == 1) return incomingEdgesData.single()
            if (incomingEdgesData.isEmpty()) return EMPTY_INIT_CONTROL_FLOW_INFO
            val variablesInScope = linkedSetOf<VariableDescriptor>()
            for (edgeData in incomingEdgesData) {
                variablesInScope.addAll(edgeData.keySet())
            }

            return variablesInScope.fold(EMPTY_INIT_CONTROL_FLOW_INFO) { result, variable ->
                var initState: InitState? = null
                var isDeclared = true
                for (edgeData in incomingEdgesData) {
                    val varControlFlowState = edgeData.getOrNull(variable)
                            ?: getDefaultValueForInitializers(variable, instruction, blockScopeVariableInfo)
                    initState = initState?.merge(varControlFlowState.initState) ?: varControlFlowState.initState
                    if (!varControlFlowState.isDeclared) {
                        isDeclared = false
                    }
                }
                if (initState == null) {
                    throw AssertionError("An empty set of incoming edges data")
                }
                result.put(variable, VariableControlFlowState.create(initState, isDeclared))
            }
        }

        // we get here if there are more than 1 branch merging in a given
        // instruction. this function resolves problem when we know about
        // the variable in a scope but it suddenly doesn't appear in
        // edge information. There we can have 2 situations:
        // it is declared before branching = the value is unknown and
        // it is given as a parameter to a function but never written to a variable
        fun setDefaultStatesForVariables(
                variable: VariableDescriptor,
                instruction: Instruction,
                blockScopeVariableInfo: BlockScopeVariableInfo
        ): VariableDataFlowState {
            val declaredIn = blockScopeVariableInfo.declaredIn[variable]
            val declaredOutside =
                    declaredIn == null
                    || declaredIn.blockScopeForContainingDeclaration != instruction.blockScope.blockScopeForContainingDeclaration
            return if (declaredOutside) getStateFromOutside(instruction)
            else VariableDataFlowState.create(VariableWithUnknownValue)
        }

        fun getStateFromOutside(instruction: Instruction): VariableDataFlowState {
            // for future use, i.e interprocedural analysis
            return VariableDataFlowState.create(VariableWithUnknownValue)
        }

        private val EMPTY_CONST_VALUE_DATA_FLOW_INFO = ConstValueControlFlowInfo()

        private fun mergeIncomingEdgesDataForConstValues(
                instruction: Instruction,
                incomingEdgesData: Collection<ConstValueControlFlowInfo>,
                blockScopeVariableInfo: BlockScopeVariableInfo
        ): ConstValueControlFlowInfo {
            if (incomingEdgesData.size == 1) return incomingEdgesData.single()
            if (incomingEdgesData.isEmpty()) return EMPTY_CONST_VALUE_DATA_FLOW_INFO
            val variablesInScope = linkedSetOf<VariableDescriptor>()
            for (edgeData in incomingEdgesData) {
                variablesInScope.addAll(edgeData.keySet())
            }

            return variablesInScope.fold(EMPTY_CONST_VALUE_DATA_FLOW_INFO) { result, variable ->
                var valueState: ValueState? = null
                for (edgeData in incomingEdgesData) {

                    val varControlFlowState = edgeData.getOrNull(variable)
                                              ?: setDefaultStatesForVariables(
                                                    variable,
                                                    instruction,
                                                    blockScopeVariableInfo
                                                )
                    valueState = valueState?.merge(varControlFlowState.valueState) ?: varControlFlowState.valueState
                    if (valueState == VariableWithNotAConstValue) break
                }
                if (valueState == null) {
                    throw AssertionError("An empty set of incoming edges data")
                }
                result.put(variable, VariableDataFlowState.create(valueState))
            }
        }

        fun createConstValueState(instruction: ReadValueInstruction) = instruction.getConstant()

        fun ReadValueInstruction.getConstant(): VariableDataFlowState? {
            val element = element
            val type = TypesResolver.resolve(element.node.elementType, element.text)
            if (type != null)
                return VariableDataFlowState.create(VariableWithConstValue(element.text, type))
            return null
        }
    }

    object TypesResolver {

        fun resolve(type: IElementType, value: String): PropagatedTypes? {
            return when(type) {
                KtNodeTypes.INTEGER_CONSTANT -> {
                    if (value.endsWith('l', true))
                        PropagatedTypes.LONG
                    PropagatedTypes.INT
                }
                KtNodeTypes.FLOAT_CONSTANT -> {
                    if (value.endsWith('f', true))
                        PropagatedTypes.FLOAT
                    PropagatedTypes.DOUBLE
                }
                KtNodeTypes.CHARACTER_CONSTANT -> PropagatedTypes.CHAR
                KtNodeTypes.BOOLEAN_CONSTANT -> PropagatedTypes.BOOLEAN
                else -> PropagatedTypes.STRING
            }
        }

    }



    class ReadOnlyConstValueControlFlowInfoImpl(
        val descriptorToConstStateMap: ImmutableHashMap<VariableDescriptor, VariableWithConstValue>,
        private val delegate: ReadOnlyConstValueControlFlowInfo?
    ) : ReadOnlyConstValueControlFlowInfo {

        fun replaceDelegate(newDelegate: ReadOnlyConstValueControlFlowInfo): ReadOnlyConstValueControlFlowInfo =
                ReadOnlyConstValueControlFlowInfoImpl(descriptorToConstStateMap, newDelegate)

        override fun getOrNull(variableDescriptor: VariableDescriptor): VariableDataFlowState? {
            if (variableDescriptor in descriptorToConstStateMap.keySet())
                return VariableDataFlowState.create(descriptorToConstStateMap[variableDescriptor].get())
            return delegate?.getOrNull(variableDescriptor)
        }

        override fun asMap(): ImmutableMap<VariableDescriptor, VariableDataFlowState> {
            val initial = delegate?.asMap() ?: ImmutableHashMap.empty()
            return descriptorToConstStateMap.keySet().fold(initial) {
                acc, variableDescriptor ->
                acc.put(variableDescriptor, getOrNull(variableDescriptor)!!)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ReadOnlyConstValueControlFlowInfoImpl

            if (descriptorToConstStateMap != other.descriptorToConstStateMap) return false
            if (delegate != other.delegate) return false

            return true
        }

        override fun hashCode(): Int {
            var result = descriptorToConstStateMap.hashCode()
            result = 31 * result + (delegate?.hashCode() ?: 0)
            return result
        }
    }

    private fun computeConstValues(): Map<Instruction, Edges<ReadOnlyConstValueControlFlowInfo>> {

        val blockScopeVariableInfo = pseudocodeVariableDataCollector.blockScopeVariableInfo

        val resultForValsWithTrivialInitializer = computeValuesForTrivialVals()

        if (rootVariables.nonTrivialVariables.isEmpty()) return resultForValsWithTrivialInitializer

        return pseudocodeVariableDataCollector.collectData(TraversalOrder.FORWARD, ConstValueControlFlowInfo()) {
            instruction: Instruction, incomingEdgesData: Collection<ConstValueControlFlowInfo> ->
            val enterInstructionData = mergeIncomingEdgesDataForConstValues(instruction, incomingEdgesData, blockScopeVariableInfo)
            val exitInstructionData = addVariableValueStateFromCurrentInstructionIfAny(
                    instruction, enterInstructionData, blockScopeVariableInfo)
            Edges(enterInstructionData, exitInstructionData)
        }.mapValues {
            (instruction, edges) ->
            val trivialEdges = resultForValsWithTrivialInitializer[instruction]!!
            Edges(trivialEdges.incoming.replaceDelegate(edges.incoming), trivialEdges.outgoing.replaceDelegate(edges.outgoing))
        }
    }

    private fun computeValuesForTrivialVals(): Map<Instruction, Edges<ReadOnlyConstValueControlFlowInfoImpl>> {
        val result = hashMapOf<Instruction, Edges<ReadOnlyConstValueControlFlowInfoImpl>>()
        var descriptorToConstStateMap = ImmutableHashMap.empty<VariableDescriptor, VariableWithConstValue>()
        pseudocode.traverse(TraversalOrder.FORWARD) { instruction ->
            val enterState = ReadOnlyConstValueControlFlowInfoImpl(descriptorToConstStateMap, null)
            when (instruction) {
                is WriteValueInstruction -> {
                    val variableDescriptor = extractValWithTrivialInitializer(instruction)
                    val previousInstruction = instruction.previousInstructions.last()
                    if (variableDescriptor != null
                        && instruction.isTrivialInitializer()
                        && previousInstruction is ReadValueInstruction
                        && (KotlinBuiltIns.isPrimitiveType(variableDescriptor.type)
                           || KotlinBuiltIns.isString(variableDescriptor.type))) {
                        println("HERE")
                        descriptorToConstStateMap =
                                descriptorToConstStateMap.put(variableDescriptor,
                                                              createConstValueState(previousInstruction)?.valueState as VariableWithConstValue)
                        println(descriptorToConstStateMap.size())
                    }
                }
            }
            val afterState = ReadOnlyConstValueControlFlowInfoImpl(descriptorToConstStateMap, null)
            result[instruction] = Edges(enterState, afterState)
        }
        return result
    }


    private fun VariableWithConstValue.sum(other: VariableWithConstValue): ValueState {
        return when (this.varType) {
            PropagatedTypes.INT -> VariableWithConstValue(
                    (this.constValue.toInt() + other.constValue.toInt()).toString(),
                    PropagatedTypes.INT)
            PropagatedTypes.DOUBLE -> VariableWithConstValue(
                    (this.constValue.toDouble() + other.constValue.toDouble()).toString(),
                    PropagatedTypes.DOUBLE)
            PropagatedTypes.FLOAT -> VariableWithConstValue(
                    (this.constValue.toFloat() + other.constValue.toFloat()).toString(),
                    PropagatedTypes.FLOAT)
            PropagatedTypes.LONG -> VariableWithConstValue(
                    (this.constValue.toLong() + other.constValue.toLong()).toString(),
                    PropagatedTypes.LONG)
            PropagatedTypes.STRING -> VariableWithConstValue(
                    this.constValue + other.constValue,
                    PropagatedTypes.STRING)

            else -> VariableWithUnknownValue
        }
    }

    private fun VariableWithConstValue.minus(other: VariableWithConstValue): ValueState {
        return when (this.varType) {
            PropagatedTypes.INT -> VariableWithConstValue(
                    (this.constValue.toInt() - other.constValue.toInt()).toString(),
                    PropagatedTypes.INT)
            PropagatedTypes.DOUBLE -> VariableWithConstValue(
                    (this.constValue.toDouble() - other.constValue.toDouble()).toString(),
                    PropagatedTypes.DOUBLE)
            PropagatedTypes.FLOAT -> VariableWithConstValue(
                    (this.constValue.toFloat() - other.constValue.toFloat()).toString(),
                    PropagatedTypes.FLOAT)
            PropagatedTypes.LONG -> VariableWithConstValue(
                    (this.constValue.toLong() - other.constValue.toLong()).toString(),
                    PropagatedTypes.LONG)

            else -> VariableWithUnknownValue
        }
    }

    private fun VariableWithConstValue.div(other: VariableWithConstValue): ValueState {
        return when (this.varType) {
            PropagatedTypes.INT -> VariableWithConstValue(
                    (this.constValue.toInt() / other.constValue.toInt()).toString(),
                    PropagatedTypes.INT)
            PropagatedTypes.DOUBLE -> VariableWithConstValue(
                    (this.constValue.toDouble() / other.constValue.toDouble()).toString(),
                    PropagatedTypes.DOUBLE)
            PropagatedTypes.FLOAT -> VariableWithConstValue(
                    (this.constValue.toFloat() / other.constValue.toFloat()).toString(),
                    PropagatedTypes.FLOAT)
            PropagatedTypes.LONG -> VariableWithConstValue(
                    (this.constValue.toLong() / other.constValue.toLong()).toString(),
                    PropagatedTypes.LONG)

            else -> VariableWithUnknownValue
        }
    }

    private fun VariableWithConstValue.times(other: VariableWithConstValue): ValueState {
        return when (this.varType) {
            PropagatedTypes.INT -> VariableWithConstValue(
                    (this.constValue.toInt() * other.constValue.toInt()).toString(),
                    PropagatedTypes.INT)
            PropagatedTypes.DOUBLE -> VariableWithConstValue(
                    (this.constValue.toDouble() * other.constValue.toDouble()).toString(),
                    PropagatedTypes.DOUBLE)
            PropagatedTypes.FLOAT -> VariableWithConstValue(
                    (this.constValue.toFloat() * other.constValue.toFloat()).toString(),
                    PropagatedTypes.FLOAT)
            PropagatedTypes.LONG -> VariableWithConstValue(
                    (this.constValue.toLong() * other.constValue.toLong()).toString(),
                    PropagatedTypes.LONG)

            else -> VariableWithUnknownValue
        }
    }

    private fun addVariableValueStateFromCurrentInstructionIfAny(
            instruction: Instruction,
            enterInstructionData: ConstValueControlFlowInfo,
            blockScopeVariableInfo: BlockScopeVariableInfo): ConstValueControlFlowInfo {

/*        if (instruction is MagicInstruction) {
            if (instruction.kind === MagicKind.EXHAUSTIVE_WHEN_ELSE) {
                return enterInstructionData.iterator().fold(enterInstructionData) {
                    result, (key, value) ->
                    if (value.valueState != VariableWithNotAConstValue) {
                        result.put(key, (instruction as ReadValueInstruction).getConstant()!!)
                    }
                    else result
                }
            }
        }
 */

        if (instruction is CallInstruction) {
            val functionDescriptor
                    = instruction.resolvedCall.resultingDescriptor as? FunctionDescriptor
            if (functionDescriptor != null && functionDescriptor.isOperator) {
                val inputValues = instruction.inputValues
                val outputValue = instruction.outputValue
                if (!inputValues.map {x -> pseudoValueToValue.containsKey(x)}.contains(false) && outputValue != null) {

                    when (functionDescriptor.name) {
                        OperatorNameConventions.PLUS -> {
                            val resState = pseudoValueToValue[inputValues[0]]!!.sum(pseudoValueToValue.get(inputValues[1])!!)
                            (resState as? VariableWithConstValue)?.let { pseudoValueToValue.put(outputValue, resState) }
                        }
                        OperatorNameConventions.MINUS -> {
                            val resState = pseudoValueToValue[inputValues[0]]!!.minus(pseudoValueToValue.get(inputValues[1])!!)
                            (resState as? VariableWithConstValue)?.let { pseudoValueToValue.put(outputValue, resState) }
                        }
                        OperatorNameConventions.DIV -> {
                            val resState = pseudoValueToValue[inputValues[0]]!!.div(pseudoValueToValue.get(inputValues[1])!!)
                            (resState as? VariableWithConstValue)?.let { pseudoValueToValue.put(outputValue, resState) }
                        }
                        OperatorNameConventions.TIMES -> {
                            val resState = pseudoValueToValue[inputValues[0]]!!.times(pseudoValueToValue.get(inputValues[1])!!)
                            (resState as? VariableWithConstValue)?.let { pseudoValueToValue.put(outputValue, resState) }
                        }
                        OperatorNameConventions.INC -> {
                            val resState = pseudoValueToValue[inputValues[0]]!!.sum(VariableWithConstValue("1",
                                                                                                             pseudoValueToValue[inputValues[0]]!!.varType))
                            (resState as? VariableWithConstValue)?.let { pseudoValueToValue.put(outputValue, resState) }
                        }
                        OperatorNameConventions.DEC -> {
                            val resState = pseudoValueToValue[inputValues[0]]!!.minus(VariableWithConstValue("1",
                                                                                                             pseudoValueToValue[inputValues[0]]!!.varType))
                            (resState as? VariableWithConstValue)?.let { pseudoValueToValue.put(outputValue, resState) }
                        }
                    }
                }
            }
        }

        if (instruction is ReadValueInstruction) {
            val varDescriptor = PseudocodeUtil.extractVariableDescriptorIfAny(instruction, bindingContext)
            if (varDescriptor != null) {
                if (enterInstructionData.containsKey(varDescriptor)) {
                    val valueState = enterInstructionData[varDescriptor]?.get()?.valueState
                    if (valueState is VariableWithConstValue) {
                        pseudoValueToValue.put(instruction.outputValue, valueState)
                    }
                }
            } else {
                createConstValueState(instruction)?.let {
                    pseudoValueToValue.put(instruction.outputValue, it.valueState as VariableWithConstValue)
                }
            }
        }

        if (instruction !is WriteValueInstruction
            && instruction !is VariableDeclarationInstruction) {
            return enterInstructionData
        }

        val variable =
                PseudocodeUtil.extractVariableDescriptorIfAny(instruction, bindingContext)
                        ?.takeIf { it in rootVariables.nonTrivialVariables }
                ?: return enterInstructionData
        var exitInstructionData = enterInstructionData

        if (instruction is WriteValueInstruction) {
            // if writing to already initialized object
//            if (!PseudocodeUtil.isThisOrNoDispatchReceiver(instruction, bindingContext)) {
//                return enterInstructionData
//            }
            val enterValueState = enterInstructionData.getOrNull(variable)
            val currentElemState: VariableDataFlowState
            if (pseudoValueToValue.containsKey(instruction.inputValues.first())) {
                currentElemState = VariableDataFlowState.create(pseudoValueToValue[instruction.inputValues.first()]!!)

            } else {
                currentElemState = VariableDataFlowState.create(VariableWithUnknownValue)
            }

            exitInstructionData =
                    exitInstructionData.put(variable, currentElemState, enterValueState)
        }
        else {
            // instruction instanceof VariableDeclarationInstruction
            val enterValueState =
                    enterInstructionData.getOrNull(variable)
                    ?: setDefaultStatesForVariables(variable, instruction, blockScopeVariableInfo)
            val variableDeclarationInfo = VariableDataFlowState.create(VariableWithUnknownValue)
            exitInstructionData = exitInstructionData.put(variable, variableDeclarationInfo, enterValueState)

        }
        return exitInstructionData
    }
}
