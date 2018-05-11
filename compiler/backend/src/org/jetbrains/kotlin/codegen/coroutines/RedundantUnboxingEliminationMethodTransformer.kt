/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen.coroutines

import org.jetbrains.kotlin.codegen.optimization.common.ControlFlowGraph
import org.jetbrains.kotlin.codegen.optimization.common.asSequence
import org.jetbrains.kotlin.codegen.optimization.transformer.MethodTransformer
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.*

/*
 * Replace CHECKCAST in {CHECKCAST Primitive, primitiveValue, Primitive.valueOf, ARETURN} sequence with ARETURN
 */
object RedundantUnboxingEliminationMethodTransformer : MethodTransformer() {
    override fun transform(internalClassName: String, methodNode: MethodNode) {
        val cfg = ControlFlowGraph.build(methodNode)
        val toDelete = findBooleanUnboxings(methodNode, cfg) + findByteUnboxings(methodNode, cfg) +
                findCharacterUnboxings(methodNode, cfg) + findShortUnboxings(methodNode, cfg) +
                findIntUnboxings(methodNode, cfg) + findLongUnboxings(methodNode, cfg) +
                findFloatUnboxings(methodNode, cfg) + findDoubleUnboxings(methodNode, cfg)
        toDelete.forEach { methodNode.instructions.set(it, InsnNode(Opcodes.ARETURN)) }
    }

    private fun findBooleanUnboxings(methodNode: MethodNode, cfg: ControlFlowGraph) =
        findUnboxings(methodNode, cfg, this::isCheckCastBoolean, this::isUnboxBoolean, this::isBoxBoolean)

    private fun findByteUnboxings(methodNode: MethodNode, cfg: ControlFlowGraph) =
        findUnboxings(methodNode, cfg, this::isCheckCastByte, this::isUnboxByte, this::isBoxByte)

    private fun findCharacterUnboxings(methodNode: MethodNode, cfg: ControlFlowGraph) =
        findUnboxings(methodNode, cfg, this::isCheckCastCharacter, this::isUnboxCharacter, this::isBoxCharacter)

    private fun findShortUnboxings(methodNode: MethodNode, cfg: ControlFlowGraph) =
        findUnboxings(methodNode, cfg, this::isCheckCastShort, this::isUnboxShort, this::isBoxShort)

    private fun findIntUnboxings(methodNode: MethodNode, cfg: ControlFlowGraph) =
        findUnboxings(methodNode, cfg, this::isCheckCastInt, this::isUnboxInt, this::isBoxInt)

    private fun findLongUnboxings(methodNode: MethodNode, cfg: ControlFlowGraph) =
        findUnboxings(methodNode, cfg, this::isCheckCastLong, this::isUnboxLong, this::isBoxLong)

    private fun findFloatUnboxings(methodNode: MethodNode, cfg: ControlFlowGraph) =
        findUnboxings(methodNode, cfg, this::isCheckCastFloat, this::isUnboxFloat, this::isBoxFloat)

    private fun findDoubleUnboxings(methodNode: MethodNode, cfg: ControlFlowGraph) =
        findUnboxings(methodNode, cfg, this::isCheckCastDouble, this::isUnboxDouble, this::isBoxDouble)

    private fun isCheckCastBoolean(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.CHECKCAST && insn.cast<TypeInsnNode>().desc == "java/lang/Boolean"

    private fun isUnboxBoolean(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.INVOKEVIRTUAL && insn.cast<MethodInsnNode>().let { it.owner == "java/lang/Boolean" && it.name == "booleanValue" }

    private fun isBoxBoolean(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.INVOKESTATIC && insn.cast<MethodInsnNode>().let { it.owner == "java/lang/Boolean" && it.name == "valueOf" }

    private fun isCheckCastByte(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.CHECKCAST && insn.cast<TypeInsnNode>().desc == "java/lang/Number"

    private fun isUnboxByte(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.INVOKEVIRTUAL && insn.cast<MethodInsnNode>().let { it.owner == "java/lang/Number" && it.name == "byteValue" }

    private fun isBoxByte(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.INVOKESTATIC && insn.cast<MethodInsnNode>().let { it.owner == "java/lang/Byte" && it.name == "valueOf" }

    private fun isCheckCastCharacter(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.CHECKCAST && insn.cast<TypeInsnNode>().desc == "java/lang/Character"

    private fun isUnboxCharacter(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.INVOKEVIRTUAL && insn.cast<MethodInsnNode>().let { it.owner == "java/lang/Character" && it.name == "charValue" }

    private fun isBoxCharacter(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.INVOKESTATIC && insn.cast<MethodInsnNode>().let { it.owner == "java/lang/Character" && it.name == "valueOf" }

    private fun isCheckCastShort(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.CHECKCAST && insn.cast<TypeInsnNode>().desc == "java/lang/Number"

    private fun isUnboxShort(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.INVOKEVIRTUAL && insn.cast<MethodInsnNode>().let { it.owner == "java/lang/Number" && it.name == "shortValue" }

    private fun isBoxShort(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.INVOKESTATIC && insn.cast<MethodInsnNode>().let { it.owner == "java/lang/Short" && it.name == "valueOf" }

    private fun isCheckCastInt(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.CHECKCAST && insn.cast<TypeInsnNode>().desc == "java/lang/Number"

    private fun isUnboxInt(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.INVOKEVIRTUAL && insn.cast<MethodInsnNode>().let { it.owner == "java/lang/Number" && it.name == "intValue" }

    private fun isBoxInt(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.INVOKESTATIC && insn.cast<MethodInsnNode>().let { it.owner == "java/lang/Integer" && it.name == "valueOf" }

    private fun isCheckCastLong(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.CHECKCAST && insn.cast<TypeInsnNode>().desc == "java/lang/Number"

    private fun isUnboxLong(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.INVOKEVIRTUAL && insn.cast<MethodInsnNode>().let { it.owner == "java/lang/Number" && it.name == "longValue" }

    private fun isBoxLong(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.INVOKESTATIC && insn.cast<MethodInsnNode>().let { it.owner == "java/lang/Long" && it.name == "valueOf" }

    private fun isCheckCastFloat(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.CHECKCAST && insn.cast<TypeInsnNode>().desc == "java/lang/Number"

    private fun isUnboxFloat(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.INVOKEVIRTUAL && insn.cast<MethodInsnNode>().let { it.owner == "java/lang/Number" && it.name == "floatValue" }

    private fun isBoxFloat(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.INVOKESTATIC && insn.cast<MethodInsnNode>().let { it.owner == "java/lang/Float" && it.name == "valueOf" }

    private fun isCheckCastDouble(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.CHECKCAST && insn.cast<TypeInsnNode>().desc == "java/lang/Number"

    private fun isUnboxDouble(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.INVOKEVIRTUAL && insn.cast<MethodInsnNode>().let { it.owner == "java/lang/Number" && it.name == "doubleValue" }

    private fun isBoxDouble(insn: AbstractInsnNode) =
        insn.opcode == Opcodes.INVOKESTATIC && insn.cast<MethodInsnNode>().let { it.owner == "java/lang/Double" && it.name == "valueOf" }

    private fun findUnboxings(
        methodNode: MethodNode,
        cfg: ControlFlowGraph,
        isCheckcast: (AbstractInsnNode) -> Boolean,
        isUnbox: (AbstractInsnNode) -> Boolean,
        isBox: (AbstractInsnNode) -> Boolean
    ): HashSet<AbstractInsnNode> {
        val checkcasts = methodNode.instructions.asSequence().filter { isCheckcast(it) }
        val res = hashSetOf<AbstractInsnNode>()
        for (checkcast in checkcasts) {
            val unbox = findImmediateSuccessors(checkcast, cfg, methodNode).singleOrNull() ?: continue
            if (!isUnbox(unbox)) continue
            val box = findImmediateSuccessors(unbox, cfg, methodNode).singleOrNull() ?: continue
            if (!isBox(box)) continue
            val areturn = findImmediateSuccessors(box, cfg, methodNode).singleOrNull() ?: continue
            if (areturn.opcode != Opcodes.ARETURN) continue
            res.add(checkcast)
        }
        return res
    }
}
