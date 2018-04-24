/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kythe.signatures

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrElementBase
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrPropertyImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrEnumEntrySymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.kythe.indexer.TypeResolver
import org.jetbrains.kotlin.kythe.indexer.TypeResolverImpl
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.psi2ir.transformations.ScopedTypeParametersResolver
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isFlexible
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * This is a prototype-implementation of a generator of stable identifiers for
 * kotlin constructions.
 *
 * Stability guarantees:
 * Generated signatures are guaranteed to be stable for one and the same
 * compilation input, as per [SignaturesProvider] javadoc.
 * However, as this is a WIP, exact format of generated signatures isn't finalized
 * and may change across different compiler versions.
 *
 * You can see examples of generated signatures in 'testData/signatures'
 */
class KotlinSignaturesProvider(symbolTable: SymbolTable) : SignaturesProvider {
    private val elementSignatureGenerator = SingleElementSignatureGenerator()
    private val signaturesCache: HashMap<IrElement, String> = hashMapOf()
    private val typeResolver: TypeResolver =
        TypeResolverImpl(
            ScopedTypeParametersResolver(),
            symbolTable
        )

    override fun enterScope(typeParametersContainer: IrTypeParametersContainer) {
        typeResolver.enterScope(typeParametersContainer)
    }

    override fun leaveScope() {
        typeResolver.exitScope()
    }

    override fun getFullSignatureOfType(kotlinType: KotlinType, immediateContext: IrTypeParametersContainer?): String {
        val descriptor = kotlinType.constructor.declarationDescriptor
                ?: throw IllegalStateException("Can't get descriptor for type $kotlinType")

        val tag = when (descriptor) {
            is TypeParameterDescriptor -> Tag.TYPE_PARAMETER
            is TypeAliasDescriptor -> Tag.TYPE_ALIAS
            is ClassDescriptor -> descriptor.kind.toIrClassKind(descriptor.isCompanionObject).tag
            else -> throw IllegalStateException("Unknown Classifier descriptor: $descriptor")
        }

        return tag.id + ":" + getSignatureForType(kotlinType, immediateContext, includeModificators = false)
    }

    override fun getFullSignature(irElement: IrElement, immediateContext: IrTypeParametersContainer?): String {
        return irElement.tag.id + ":" + getSignatureForElement(irElement, immediateContext) + irElement.returnTypeSignature()
    }

    private fun getSignatureForType(
        type: KotlinType,
        immediateContext: IrTypeParametersContainer?,
        includeModificators: Boolean = true
    ): String {
        val symbol = typeResolver.resolveToSymbol(type)!!

        val signatureForType = if (!symbol.isBound) {
            val unsubstitutedType = tryGetSignatureForBuiltin(symbol.descriptor)
                    ?: tryGetSignatureForJavaClass(symbol.descriptor)
                    ?: throw IllegalStateException("Symbol for descriptor ${symbol.descriptor} isn't bound, but it is not a built-in")
            val signatureForArgumentsIfAny = type.arguments.takeIf { it.isNotEmpty() }?.joinToString(prefix = "<", postfix = ">") {
                getSignatureForType(it.type, immediateContext)
            } ?: ""

            unsubstitutedType + signatureForArgumentsIfAny
        } else {
            // NB. This will generate type arguments
            getSignatureForElement(symbol.owner, immediateContext)
        }

        val modificator = when {
            !includeModificators -> ""
            type.isMarkedNullable -> "?"
            type.isFlexible() -> "!"
            else -> ""
        }

        return signatureForType + modificator
    }

    private fun tryGetSignatureForBuiltin(descriptor: DeclarationDescriptor): String? {
        if (!KotlinBuiltIns.isUnderKotlinPackage(descriptor)) return null
        return descriptor.fqNameUnsafe.asString()
    }

    private fun tryGetSignatureForJavaClass(descriptor: DeclarationDescriptor): String? {
        // TODO: support java signatures
        return when (descriptor) {
            is JavaClassDescriptor -> descriptor.fqNameUnsafe.asString()
            else -> null
        }
    }


    private fun getSignatureForElement(irElement: IrElement, immediateContext: IrTypeParametersContainer?): String {
        if (irElement is IrTypeParameter && immediateContext?.typeParameters?.contains(irElement) == true) {
            // I.e. we're looking for signature for type of 'x' in the declaration 'fun <T> foo(x: T)'
            // In such cases we render TypeParameter as just 'T', without prepending parent's signature,
            // because it would lead to infinite recursion.
            return irElement.name.asString()
        }

        return buildString {
            if (signaturesCache.containsKey(irElement)) {
                this.append(signaturesCache[irElement])
            } else {
                val pathFromRoot = irElement.parentsWithoutMe().asReversed()

                for (parent in pathFromRoot) {
                    // Note that this doesn't result in O(n^2) -- "grandparent's" signature
                    // is guaranteed to be computed and cached on the previous step
                    parent.accept(elementSignatureGenerator, this)
                }

                irElement.accept(elementSignatureGenerator, this)
                signaturesCache[irElement] = this.toString()
            }
        }
    }

    private fun IrElement.returnTypeSignature(): String {
        val type = when (this) {
            is IrFunction -> returnType
            is IrProperty -> type
            is IrValueParameter -> type
            else -> return ""
        }

        return ";" + getSignatureForType(type, this as? IrTypeParametersContainer)
    }

    // Writes signature of a given element in passed 'StringBuilder'
    private inner class SingleElementSignatureGenerator : IrElementVisitor<Unit, StringBuilder> {

        override fun visitElement(element: IrElement, data: StringBuilder) {
            throw IllegalStateException("Don't know how to generate signature for $element")
        }

        override fun visitPackageFragment(declaration: IrPackageFragment, data: StringBuilder) {
            data.append(declaration.fqName.asString() + ".")
        }

        override fun visitClass(declaration: IrClass, data: StringBuilder) {
            if (declaration.parent !is IrFile && declaration.parent !is IrExternalPackageFragment) { // Non-top level class <=> nested or inner
                if (declaration.descriptor.isInner) data.append("$") else data.append(".")
            }

            val typeParameters = declaration.typeParameters.joinTypeParametersTypes(declaration)
            data.append(declaration.name).append(typeParameters)
        }

        override fun visitTypeAlias(declaration: IrTypeAlias, data: StringBuilder) {
            throw IllegalStateException("Type aliases are not supported yet")
        }

        override fun visitSimpleFunction(declaration: IrSimpleFunction, data: StringBuilder) {
            if (declaration.parent !is IrFile && declaration.parent !is IrExternalPackageFragment) {
                data.append(".")
            }

            val typeParameters = declaration.typeParameters.joinTypeParametersTypes(declaration)
            val valueParameters = declaration.valueParameters.joinValueParametersTypes(declaration)

            data.append(typeParameters).append(declaration.name).append(valueParameters)
        }

        override fun visitConstructor(declaration: IrConstructor, data: StringBuilder) {
            val symbol = typeResolver.resolveToSymbol(declaration.returnType)
            val ownerClassName = when (symbol) {
                is IrClassSymbol -> symbol.owner.name
                is IrTypeParameterSymbol -> symbol.owner.name
                is IrEnumEntrySymbol -> symbol.owner.name
                else -> throw IllegalStateException("Unrecognized IrClassifierSymbol: $symbol")
            }

            data.append(".").append(ownerClassName)
                .append(declaration.valueParameters.joinValueParametersTypes(declaration))
        }

        override fun visitProperty(declaration: IrProperty, data: StringBuilder) {
            if (declaration.parent !is IrFile && declaration.parent !is IrExternalPackageFragment) {
                data.append(".")
            }
            data.append(declaration.name)
        }

        override fun visitVariable(declaration: IrVariable, data: StringBuilder) {
            data.append(".").append(declaration.name)
        }

        override fun visitField(declaration: IrField, data: StringBuilder) {
            data.append("#").append("field")
        }

        override fun visitTypeParameter(declaration: IrTypeParameter, data: StringBuilder) {
            data.append("~").append(declaration.name)
        }

        override fun visitValueParameter(declaration: IrValueParameter, data: StringBuilder) {
            data.append("#").append(declaration.name)
        }

        override fun visitEnumEntry(declaration: IrEnumEntry, data: StringBuilder) {
            data.append(".").append(declaration.name)
        }
    }

    private fun IrElement.parentsWithoutMe(): List<IrElement> =
        generateSequence(this.nextParent) { it.nextParent }.toList()

    private val IrElement.nextParent: IrElement?
        get() = when (this) {
        // Workaround quirk of IR: parent of property accessors is class instead of a property
            is IrFunction -> if (isAccessor()) getParentPropertyOfAccessor() else parent as? IrElement
            is IrField -> getParentPropertyOfBackingField()
            is IrDeclaration -> parent as? IrElement
            else -> null
        }

    private fun IrFunction.isAccessor(): Boolean = this.descriptor is PropertyAccessorDescriptor

    private fun IrFunction.getParentPropertyOfAccessor(): IrElement? {
        // XXX: hack and create IrProperty ad hoc, because it is not a symbol in IR, and symbol table doesn't know about IrProperties
        val correspondingPropertyDescriptor = this.descriptor.safeAs<PropertyAccessorDescriptor>()?.correspondingProperty ?: return null
        return IrPropertyImpl(
            startOffset = -1,
            endOffset = -1,
            origin = IrDeclarationOrigin.DEFINED,
            isDelegated = correspondingPropertyDescriptor.isDelegated,
            descriptor = correspondingPropertyDescriptor
        ).also { property -> property.parent = this.parent }
    }

    private fun IrField.getParentPropertyOfBackingField(): IrElement? {
        val correspondingPropertyDescriptor = this.descriptor
        return IrPropertyImpl(
            startOffset = -1,
            endOffset = -1,
            origin = IrDeclarationOrigin.DEFINED,
            isDelegated = correspondingPropertyDescriptor.isDelegated,
            descriptor = correspondingPropertyDescriptor
        ).also { property -> property.parent = this.parent }
    }


    private fun List<IrValueParameter>.joinValueParametersTypes(owner: IrElement): String =
        joinToString(prefix = "(", postfix = ")") {
            getSignatureForType(it.type, owner as? IrTypeParametersContainer)
        }

    private fun List<IrTypeParameter>.joinTypeParametersTypes(owner: IrTypeParametersContainer): String {
        if (isEmpty()) return ""

        return joinToString(prefix = "<", postfix = ">") {
            getSignatureForType(it.descriptor.defaultType, owner)
        }
    }

    private val IrElement.tag: Tag
        get() = when (this) {
            is IrPackageFragment -> Tag.PACKAGE_FRAGMENT
            is IrClass -> this.irClassKind.tag
            is IrDeclaration -> when (this.declarationKind) {
                IrDeclarationKind.ENUM_ENTRY -> Tag.ENUM_ENTRY
                IrDeclarationKind.FUNCTION -> Tag.FUNCTION
                IrDeclarationKind.CONSTRUCTOR -> Tag.CONSTRUCTOR
                IrDeclarationKind.PROPERTY -> Tag.PROPERTY
                IrDeclarationKind.FIELD -> Tag.BACKING_FIELD
                IrDeclarationKind.VARIABLE -> Tag.VARIABLE
                IrDeclarationKind.LOCAL_PROPERTY -> Tag.LOCAL_PROPERTY
                IrDeclarationKind.TYPEALIAS -> throw IllegalStateException("Type aliases are not supported yet")
                IrDeclarationKind.TYPE_PARAMETER -> Tag.TYPE_PARAMETER
                IrDeclarationKind.VALUE_PARAMETER -> Tag.VALUE_PARAMETER
                else -> throw IllegalStateException("Don't know how to generate tag for $this")
            }
            else -> throw IllegalStateException("Don't know how to get Tag for $this")
        }

    private val IrClassKind.tag: Tag
        get() = when (this) {
            IrClassKind.CLASS -> Tag.CLASS
            IrClassKind.INTERFACE -> Tag.INTERFACE
            IrClassKind.ENUM_CLASS -> Tag.ENUM_CLASS
            IrClassKind.ENUM_ENTRY -> Tag.ENUM_ENTRY
            IrClassKind.ANNOTATION_CLASS -> throw IllegalStateException("Annotations are not supported yet")
            IrClassKind.OBJECT -> Tag.OBJECT
            IrClassKind.COMPANION -> Tag.COMPANION_OBJECT
        }

    enum class Tag(val id: String) {
        PACKAGE_FRAGMENT("PACK"),

        CLASS("CLASS"),
        INTERFACE("INTERFACE"),
        OBJECT("OBJ"),
        COMPANION_OBJECT("COMP"),
        ENUM_CLASS("ENUM"),
        ENUM_ENTRY("ENUM_ENTRY"),
        TYPE_ALIAS("TALIAS"),

        FUNCTION("FUN"),
        CONSTRUCTOR("CTOR"),
        PROPERTY("PROP"),
        BACKING_FIELD("BFIELD"),
        GETTER("GET"),
        SETTER("SET"),

        TYPE_PARAMETER("TPARAM"),
        VALUE_PARAMETER("VPARAM"),
        LOCAL_PROPERTY("LPROP"), // TODO: drop from signatures?
        VARIABLE("VAR"), // TODO: I think we have either merge those two or distiguish them clearly

    }
}