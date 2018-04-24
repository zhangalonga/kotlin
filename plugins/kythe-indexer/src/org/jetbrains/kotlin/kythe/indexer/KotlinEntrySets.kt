/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kythe.indexer

import com.google.devtools.kythe.analyzers.base.*
import com.google.devtools.kythe.platform.shared.StatisticsCollector
import com.google.devtools.kythe.proto.Analysis
import com.google.devtools.kythe.proto.Storage
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.declarations.IrTypeParametersContainer
import org.jetbrains.kotlin.ir.declarations.toIrClassKind
import org.jetbrains.kotlin.ir.expressions.IrDeclarationReference
import org.jetbrains.kotlin.kythe.signatures.SignaturesProvider
import org.jetbrains.kotlin.resolve.calls.inference.CapturedType
import org.jetbrains.kotlin.types.FlexibleType
import org.jetbrains.kotlin.types.KotlinType
import java.lang.UnsupportedOperationException

class KotlinEntrySets(
    statisticsCollector: StatisticsCollector,
    emitter: FactEmitter,
    compilationVName: Storage.VName,
    requiredInputs: MutableList<Analysis.CompilationUnit.FileInput>,
    private val signaturesProvider: SignaturesProvider
) : KytheEntrySets(statisticsCollector, emitter, compilationVName, requiredInputs) {
    private val absNodesCache: HashMap<Storage.VName, EntrySet> = hashMapOf()
    private val corpus: String = ""
    private val root: String = ""
    private val path: String = ""
    private val language: String = "kotlin"

    fun getVName(irElement: IrElement): Storage.VName {
        return signaturesProvider.getFullSignature(irElement, (irElement as? IrDeclaration)?.parent as? IrTypeParametersContainer).toVName()
    }

    // Conceptual difference from `getVName` is that this method keeps kythe referencing
    // semantic, e.g. for Generic class it will return VName for 'abs' node (not for 'record/class' node)
    fun getReferencedVName(irElement: IrElement): Storage.VName {
        val owner = when (irElement) {
            is IrDeclarationReference -> irElement.symbol.owner
            is IrSymbolOwner -> irElement.symbol.owner
            else -> throw IllegalStateException("Don't know how to resolve reference target for $irElement")
        }
        return getVName(owner)
    }

    fun enterScope(irTypeParametersContainer: IrTypeParametersContainer) {
        signaturesProvider.enterScope(irTypeParametersContainer)
    }

    fun leaveScope() {
        signaturesProvider.leaveScope()
    }

    // vNameForWholeType -- vname for e.g. TYPEOF edge (i.e. vName of the whole type, with type arguments taken into consideration)
    // vNameForReferencing -- vname for e.g. REF edge (i.e. vName of the corresponding unsubstituted type)
    // For non-generic types, vNameForWholeType === vNameForReferencing
    data class TypeReferenceVNames(val vNameForWholeType: Storage.VName, val vNameForReferencing: Storage.VName)

    fun getVNameForTypeReference(kotlinType: KotlinType, immediateContext: IrTypeParametersContainer?): TypeReferenceVNames {
        if (kotlinType.unwrap() is CapturedType || kotlinType.unwrap() is FlexibleType) {
            throw UnsupportedOperationException("Captured/Flexible types are not supported yet")
        }

        return if (kotlinType.arguments.isEmpty()) {
            val node = NodeBuilder(
                kotlinType.toNodeKind(),
                signaturesProvider.getFullSignatureOfType(kotlinType, immediateContext).toVName()
            ).build()

            node.emit(emitter)

            // Non-generic type: vnames for whole type and for referenced type are the same
            TypeReferenceVNames(node.vName, node.vName)
        } else {
            getVNamesForGenericType(kotlinType, immediateContext)
        }
    }

    private fun getVNamesForGenericType(kotlinType: KotlinType, immediateContext: IrTypeParametersContainer?): TypeReferenceVNames {
        // If 'kotlinType' is generic like 'Foo<String, Int, Bar<Int, Int?>>', then 'unsubstitutedType' is just Foo<T, Q, R>
        val unsubstitutedType = kotlinType.constructor.declarationDescriptor?.original?.defaultType!!
        val vNameForUnsubstitutedType = signaturesProvider.getFullSignatureOfType(unsubstitutedType, immediateContext).toVName()

        val absNode = absNodesCache.getOrPut(vNameForUnsubstitutedType) {
            val vNameForTypeParameters = unsubstitutedType.arguments.map {
                signaturesProvider.getFullSignatureOfType(it.type, immediateContext).toVName()
            }

            newAbstractAndEmit(vNameForUnsubstitutedType, vNameForTypeParameters, null).also { it.emit(emitter) }
        }

        val vNameForWholeType = if (kotlinType.unwrap() === unsubstitutedType) {
            // If 'kotlinType' was unsubsistuted (i.e. 'Foo<T, Q, R>), then there are no TApply node for that type,
            // and just abs node corresponds to it
            absNode
        } else {
            // Otherwise, there are some type projections, and we have to emit nodes for all of them,
            // as well as corresponding tapp node
            val argumentsVNames = kotlinType.arguments.map { getVNameForTypeReference(it.type, immediateContext).vNameForReferencing }
            newTApplyAndEmit(absNode.vName, argumentsVNames)
        }

        return TypeReferenceVNames(
            vNameForWholeType = vNameForWholeType.vName,
            vNameForReferencing = absNode.vName
        )
    }

    private fun String.toVName(): Storage.VName {
        return Storage.VName.newBuilder()
            .setSignature(this)
            .setCorpus(corpus)
            .setRoot(root)
            .setPath(path)
            .setLanguage(language)
            .build()
    }

    private fun KotlinType.toNodeKind(): NodeKind {
        val declarationDescriptor = constructor.declarationDescriptor
                ?: throw IllegalStateException("Can't find declaration descriptor for type $this")

        return when (declarationDescriptor) {
            is TypeParameterDescriptor -> NodeKind.ABS_VAR
            is TypeAliasDescriptor -> throw UnsupportedOperationException("Type aliases are not supported yet")
            is ClassDescriptor -> declarationDescriptor.kind.toIrClassKind(declarationDescriptor.isCompanionObject).toNodeKind()
            else -> throw IllegalStateException("Unknown declaration descriptor of $this: $declarationDescriptor")
        }
    }
}

