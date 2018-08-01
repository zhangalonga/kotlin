package org.jetbrains.kotlin.ir.util

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSetFieldImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.IrStarProjectionImpl
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.OverridingStrategy
import org.jetbrains.kotlin.resolve.OverridingUtil
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.typeUtil.immediateSupertypes
import java.lang.reflect.Proxy

fun IrClass.addFakeOverrides(symbolTable: SymbolTable) {

    val startOffset = this.startOffset
    val endOffset = this.endOffset

    descriptor.unsubstitutedMemberScope.getContributedDescriptors()
            .filterIsInstance<CallableMemberDescriptor>()
            .filter { it.kind == CallableMemberDescriptor.Kind.FAKE_OVERRIDE }
            .forEach {
                this.addChild(createFakeOverride(it, startOffset, endOffset, symbolTable))
            }
}

private fun createFakeOverride(
        descriptor: CallableMemberDescriptor,
        startOffset: Int,
        endOffset: Int,
        symbolTable: SymbolTable
): IrDeclaration {

    fun FunctionDescriptor.createFunction(): IrSimpleFunction = IrFunctionImpl(
            startOffset, endOffset,
            IrDeclarationOrigin.FAKE_OVERRIDE, this
    ).apply {
        returnType = symbolTable.translateErased(this@createFunction.returnType!!)
        createParameterDeclarations(symbolTable)
    }

    return when (descriptor) {
        is FunctionDescriptor -> descriptor.createFunction()
        is PropertyDescriptor ->
            IrPropertyImpl(startOffset, endOffset, IrDeclarationOrigin.FAKE_OVERRIDE, descriptor).apply {
                // TODO: add field if getter is missing?
                getter = descriptor.getter?.createFunction() as IrSimpleFunction?
                setter = descriptor.setter?.createFunction() as IrSimpleFunction?
            }
        else -> TODO(descriptor.toString())
    }
}

fun IrFunction.createParameterDeclarations(symbolTable: SymbolTable) {

    fun ParameterDescriptor.irValueParameter() = IrValueParameterImpl(
            innerStartOffset(this), innerEndOffset(this),
            IrDeclarationOrigin.DEFINED,
            this, symbolTable.translateErased(this.type),
            (this as? ValueParameterDescriptor)?.varargElementType?.let { symbolTable.translateErased(it) }
    ).also {
        it.parent = this@createParameterDeclarations
    }

    dispatchReceiverParameter = descriptor.dispatchReceiverParameter?.irValueParameter()
    extensionReceiverParameter = descriptor.extensionReceiverParameter?.irValueParameter()

    assert(valueParameters.isEmpty())
    descriptor.valueParameters.mapTo(valueParameters) { it.irValueParameter() }

    assert(typeParameters.isEmpty())
    descriptor.typeParameters.mapTo(typeParameters) {
        IrTypeParameterImpl(
                innerStartOffset(it), innerEndOffset(it),
                IrDeclarationOrigin.DEFINED,
                it
        ).also { typeParameter ->
            typeParameter.parent = this
            typeParameter.descriptor.upperBounds.mapTo(typeParameter.superTypes, symbolTable::translateErased)
        }
    }
}

private fun createFakeOverride(
        descriptor: CallableMemberDescriptor,
        overriddenDeclarations: List<IrDeclaration>,
        irClass: IrClass
): IrDeclaration {

    // TODO: this function doesn't substitute types.
    fun IrSimpleFunction.copyFake(descriptor: FunctionDescriptor): IrSimpleFunction = IrFunctionImpl(
            irClass.startOffset, irClass.endOffset, IrDeclarationOrigin.FAKE_OVERRIDE, descriptor
    ).also {
        it.returnType = returnType
        it.parent = irClass
        it.createDispatchReceiverParameter()

        it.extensionReceiverParameter = this.extensionReceiverParameter?.let {
            IrValueParameterImpl(
                    it.startOffset,
                    it.endOffset,
                    IrDeclarationOrigin.DEFINED,
                    it.descriptor.extensionReceiverParameter!!,
                    it.type,
                    null
            )
        }

        this.valueParameters.mapTo(it.valueParameters) { oldParameter ->
            IrValueParameterImpl(
                    oldParameter.startOffset,
                    oldParameter.endOffset,
                    IrDeclarationOrigin.DEFINED,
                    it.descriptor.valueParameters[oldParameter.index],
                    oldParameter.type,
                    (oldParameter as? IrValueParameter)?.varargElementType
            )
        }

        this.typeParameters.mapTo(it.typeParameters) { oldParameter ->
            IrTypeParameterImpl(
                    irClass.startOffset,
                    irClass.endOffset,
                    IrDeclarationOrigin.DEFINED,
                    it.descriptor.typeParameters[oldParameter.index]
            ).apply {
                superTypes += oldParameter.superTypes
            }
        }
    }

    val copiedDeclaration = overriddenDeclarations.first()

    return when (copiedDeclaration) {
        is IrSimpleFunction -> copiedDeclaration.copyFake(descriptor as FunctionDescriptor)
        is IrProperty -> IrPropertyImpl(
                irClass.startOffset,
                irClass.endOffset,
                IrDeclarationOrigin.FAKE_OVERRIDE,
                descriptor as PropertyDescriptor
        ).apply {
            parent = irClass
            getter = copiedDeclaration.getter?.copyFake(descriptor.getter!!)
            setter = copiedDeclaration.setter?.copyFake(descriptor.setter!!)
        }
        else -> error(copiedDeclaration)
    }
}


fun IrSimpleFunction.setOverrides(symbolTable: ReferenceSymbolTable) {
    assert(this.overriddenSymbols.isEmpty())

    this.descriptor.overriddenDescriptors.mapTo(this.overriddenSymbols) {
        symbolTable.referenceSimpleFunction(it.original)
    }
}

fun IrClass.simpleFunctions(): List<IrSimpleFunction> = this.declarations.flatMap {
    when (it) {
        is IrSimpleFunction -> listOf(it)
        is IrProperty -> listOfNotNull(it.getter as IrSimpleFunction?, it.setter as IrSimpleFunction?)
        else -> emptyList()
    }
}

fun IrClass.createParameterDeclarations() {
    thisReceiver = IrValueParameterImpl(
            startOffset, endOffset,
            IrDeclarationOrigin.INSTANCE_RECEIVER,
            descriptor.thisAsReceiverParameter,
            this.symbol.typeWith(this.typeParameters.map { it.defaultType }),
            null
    ).also { valueParameter ->
        valueParameter.parent = this
    }

    assert(typeParameters.isEmpty())
    assert(descriptor.declaredTypeParameters.isEmpty())
}

fun IrClass.createParameterDeclarations(symbolTable: SymbolTable) {
    this.descriptor.declaredTypeParameters.mapTo(this.typeParameters) {
        IrTypeParameterImpl(startOffset, endOffset, IrDeclarationOrigin.DEFINED, it).apply {
            it.upperBounds.mapTo(this.superTypes, symbolTable::translateErased)
        }
    }

    this.thisReceiver = IrValueParameterImpl(
            startOffset, endOffset,
            IrDeclarationOrigin.DEFINED,
            this.descriptor.thisAsReceiverParameter,
            this.symbol.typeWith(this.typeParameters.map { it.defaultType }),
            null
    )
}

fun IrClass.setSuperSymbols(superTypes: List<IrType>) {
    val supers = superTypes.map { it.getClass()!! }
    assert(this.superDescriptors().toSet() == supers.map { it.descriptor }.toSet())
    assert(this.superTypes.isEmpty())
    this.superTypes += superTypes

    val superMembers = supers.flatMap {
        it.simpleFunctions()
    }.associateBy { it.descriptor }

    this.simpleFunctions().forEach {
        assert(it.overriddenSymbols.isEmpty())

        it.descriptor.overriddenDescriptors.mapTo(it.overriddenSymbols) {
            val superMember = superMembers[it.original] ?: error(it.original)
            superMember.symbol
        }
    }
}

private fun IrClass.superDescriptors() =
        this.descriptor.typeConstructor.supertypes.map { it.constructor.declarationDescriptor as ClassDescriptor }

fun IrClass.setSuperSymbols(symbolTable: ReferenceSymbolTable) {
    assert(this.superTypes.isEmpty())
    this.descriptor.typeConstructor.supertypes.mapTo(this.superTypes) { symbolTable.translateErased(it) }

    this.simpleFunctions().forEach {
        it.setOverrides(symbolTable)
    }
}

private fun IrElement.innerStartOffset(descriptor: DeclarationDescriptorWithSource): Int =
        descriptor.startOffset ?: this.startOffset

private fun IrElement.innerEndOffset(descriptor: DeclarationDescriptorWithSource): Int =
        descriptor.endOffset ?: this.endOffset

inline fun <reified T> stub(name: String): T {
    return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) {
        _ /* proxy */, method, _ /* methodArgs */ ->
        if (method.name == "toString" && method.parameterCount == 0) {
            "${T::class.simpleName} stub for $name"
        } else {
            error("${T::class.simpleName}.${method.name} is not supported for $name")
        }
    } as T
}

fun IrDeclarationContainer.addChild(declaration: IrDeclaration) {
    this.declarations += declaration
    declaration.accept(SetDeclarationsParentVisitor, this)
}

object SetDeclarationsParentVisitor : IrElementVisitor<Unit, IrDeclarationParent> {
    override fun visitElement(element: IrElement, data: IrDeclarationParent) {
        if (element !is IrDeclarationParent) {
            element.acceptChildren(this, data)
        }
    }

    override fun visitDeclaration(declaration: IrDeclaration, data: IrDeclarationParent) {
        declaration.parent = data
        super.visitDeclaration(declaration, data)
    }
}

fun IrModuleFragment.checkDeclarationParents() {
    this.accept(CheckDeclarationParentsVisitor, null)
}

object CheckDeclarationParentsVisitor : IrElementVisitor<Unit, IrDeclarationParent?> {

    override fun visitElement(element: IrElement, data: IrDeclarationParent?) {
        element.acceptChildren(this, element as? IrDeclarationParent ?: data)
    }

    override fun visitDeclaration(declaration: IrDeclaration, data: IrDeclarationParent?) {
        if (declaration !is IrVariable && declaration !is IrValueParameter && declaration !is IrTypeParameter) {
            checkParent(declaration, data)
        } else {
            // Don't check IrVariable parent.
        }

        super.visitDeclaration(declaration, data)
    }

    private fun checkParent(declaration: IrDeclaration, expectedParent: IrDeclarationParent?) {
        val parent = try {
            declaration.parent
        } catch (e: Throwable) {
            error("$declaration for ${declaration.descriptor} has no parent")
        }

        if (parent != expectedParent) {
            error("$declaration for ${declaration.descriptor} has unexpected parent $parent")
        }
    }
}

tailrec fun IrDeclaration.getContainingFile(): IrFile? {
    val parent = this.parent

    return when (parent) {
        is IrFile -> parent
        is IrDeclaration -> parent.getContainingFile()
        else -> null
    }
}


fun IrType.substitute(map: Map<IrTypeParameterSymbol, IrType>): IrType {
    if (this !is IrSimpleType) return this

    val classifier = this.classifier
    return when (classifier) {
        is IrTypeParameterSymbol ->
            map[classifier]?.let { if (this.hasQuestionMark) it.makeNullable() else it }
                    ?: this

        is IrClassSymbol -> if (this.arguments.isEmpty()) {
            this // Fast path.
        } else {
            val newArguments = this.arguments.map {
                when (it) {
                    is IrTypeProjection -> makeTypeProjection(it.type.substitute(map), it.variance)
                    is IrStarProjection -> it
                    else -> error(it)
                }
            }
            IrSimpleTypeImpl(classifier, hasQuestionMark, newArguments, annotations)
        }
        else -> error(classifier)
    }

}


fun CallableMemberDescriptor.createValueParameter(
        index: Int,
        name: String,
        type: IrType,
        startOffset: Int,
        endOffset: Int
): IrValueParameter {
    val descriptor = ValueParameterDescriptorImpl(
            this, null,
            index,
            Annotations.EMPTY,
            Name.identifier(name),
            type.toKotlinType(),
            false, false, false, null, SourceElement.NO_SOURCE
    )

    return IrValueParameterImpl(startOffset, endOffset, IrDeclarationOrigin.DEFINED, descriptor, type, null)
}

fun ReferenceSymbolTable.translateErased(type: KotlinType): IrSimpleType {
    val descriptor = TypeUtils.getClassDescriptor(type)
    if (descriptor == null) return translateErased(type.immediateSupertypes().first())
    val classSymbol = this.referenceClass(descriptor)

    val nullable = type.isMarkedNullable
    val arguments = type.arguments.map { IrStarProjectionImpl }

    return classSymbol.createType(nullable, arguments)
}


fun createField(
        startOffset: Int,
        endOffset: Int,
        type: IrType,
        name: Name,
        isMutable: Boolean,
        origin: IrDeclarationOrigin,
        owner: ClassDescriptor
): IrField {
    val descriptor = PropertyDescriptorImpl.create(
            /* containingDeclaration = */ owner,
            /* annotations           = */ Annotations.EMPTY,
            /* modality              = */ Modality.FINAL,
            /* visibility            = */ Visibilities.PRIVATE,
            /* isVar                 = */ isMutable,
            /* name                  = */ name,
            /* kind                  = */ CallableMemberDescriptor.Kind.DECLARATION,
            /* source                = */ SourceElement.NO_SOURCE,
            /* lateInit              = */ false,
            /* isConst               = */ false,
            /* isExpect              = */ false,
            /* isActual                = */ false,
            /* isExternal            = */ false,
            /* isDelegated           = */ false
    ).apply {
        initialize(null, null)

        val receiverType: KotlinType? = null
        setType(type.toKotlinType(), emptyList(), owner.thisAsReceiverParameter, receiverType)
    }

    return IrFieldImpl(startOffset, endOffset, origin, descriptor, type)
}

val IrTypeArgument.typeOrNull: IrType? get() = (this as? IrTypeProjection)?.type

val IrType.isSimpleTypeWithQuestionMark: Boolean
    get() = this is IrSimpleType && this.hasQuestionMark
