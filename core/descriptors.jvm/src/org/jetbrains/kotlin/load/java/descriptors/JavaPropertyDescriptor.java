/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.load.java.descriptors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.builtins.KotlinBuiltIns;
import org.jetbrains.kotlin.descriptors.*;
import org.jetbrains.kotlin.descriptors.annotations.Annotations;
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl;
import org.jetbrains.kotlin.descriptors.impl.PropertyGetterDescriptorImpl;
import org.jetbrains.kotlin.descriptors.impl.PropertySetterDescriptorImpl;
import org.jetbrains.kotlin.load.java.typeEnhancement.TypeEnhancementKt;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.types.KotlinType;

import java.util.List;

public class JavaPropertyDescriptor extends PropertyDescriptorImpl implements JavaCallableMemberDescriptor {
    private final boolean isStaticFinal;

    private JavaPropertyDescriptor(
            @NotNull DeclarationDescriptor containingDeclaration,
            @NotNull Annotations annotations,
            @NotNull Modality modality,
            @NotNull Visibility visibility,
            boolean isVar,
            @NotNull Name name,
            @NotNull SourceElement source,
            @Nullable PropertyDescriptor original,
            @NotNull Kind kind,
            boolean isStaticFinal
    ) {
        super(containingDeclaration, original, annotations, modality, visibility, isVar, name, kind, source,
              false, false, false, false, false, false);

        this.isStaticFinal = isStaticFinal;
    }

    @NotNull
    public static JavaPropertyDescriptor create(
            @NotNull DeclarationDescriptor containingDeclaration,
            @NotNull Annotations annotations,
            @NotNull Modality modality,
            @NotNull Visibility visibility,
            boolean isVar,
            @NotNull Name name,
            @NotNull SourceElement source,
            boolean isStaticFinal
    ) {
        return new JavaPropertyDescriptor(
                containingDeclaration, annotations, modality, visibility, isVar, name, source, null, Kind.DECLARATION, isStaticFinal
        );
    }

    @NotNull
    @Override
    protected PropertyDescriptorImpl createSubstitutedCopy(
            @NotNull DeclarationDescriptor newOwner,
            @NotNull Annotations annotations,
            @NotNull Modality newModality,
            @NotNull Visibility newVisibility,
            @Nullable PropertyDescriptor original,
            @NotNull Kind kind,
            @NotNull Name newName
    ) {
        return new JavaPropertyDescriptor(
                newOwner, getAnnotations(), newModality, newVisibility, isVar(), newName, SourceElement.NO_SOURCE, original,
                kind, isStaticFinal
        );
    }

    @Override
    public boolean hasSynthesizedParameterNames() {
        return false;
    }

    @NotNull
    @Override
    public JavaCallableMemberDescriptor enhance(
            @Nullable KotlinType enhancedReceiverType,
            @NotNull List<ValueParameterData> enhancedValueParametersData,
            @NotNull KotlinType enhancedReturnType
    ) {
        JavaPropertyDescriptor enhanced = new JavaPropertyDescriptor(
                getContainingDeclaration(),
                getAnnotations(),
                getModality(),
                getVisibility(),
                isVar(),
                getName(),
                getSource(),
                getOriginal(),
                getKind(),
                isStaticFinal
        );

        PropertyGetterDescriptorImpl newGetter = null;
        PropertyGetterDescriptorImpl getter = getGetter();
        if (getter != null) {
            newGetter = new PropertyGetterDescriptorImpl(
                    enhanced, getter.getAnnotations(), getter.getModality(), getter.getVisibility(),
                    getter.isDefault(), getter.isExternal(), getter.isInline(), getKind(), getter, getter.getSource()
            );
            newGetter.setInitialSignatureDescriptor(getter.getInitialSignatureDescriptor());
            newGetter.initialize(enhancedReturnType);
        }

        PropertySetterDescriptorImpl newSetter = null;
        PropertySetterDescriptor setter = getSetter();
        if (setter != null) {
            newSetter = new PropertySetterDescriptorImpl(
                    enhanced, setter.getAnnotations(), setter.getModality(), setter.getVisibility(),
                    setter.isDefault(), setter.isExternal(), setter.isInline(), getKind(), setter, setter.getSource()
            );
            newSetter.setInitialSignatureDescriptor(newSetter.getInitialSignatureDescriptor());
            newSetter.initialize(setter.getValueParameters().get(0));
        }

        enhanced.initialize(newGetter, newSetter);
        enhanced.setSetterProjectedOut(isSetterProjectedOut());
        if (compileTimeInitializer != null) {
            enhanced.setCompileTimeInitializer(compileTimeInitializer);
        }

        enhanced.setOverriddenDescriptors(getOverriddenDescriptors());

        enhanced.setType(
                enhancedReturnType,
                getTypeParameters(), // TODO
                getDispatchReceiverParameter(),
                enhancedReceiverType
        );
        return enhanced;
    }

    @Override
    public boolean isConst() {
        KotlinType type = getType();
        return isStaticFinal && ConstUtil.canBeUsedForConstVal(type) &&
               (!TypeEnhancementKt.hasEnhancedNullability(type) || KotlinBuiltIns.isString(type));
    }
}
