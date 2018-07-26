/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package kotlin.contracts

import kotlin.internal.ContractsDsl

@ContractsDsl
@SinceKotlin("1.2")
interface Effect

@ContractsDsl
@SinceKotlin("1.2")
interface ConditionalEffect : Effect

@ContractsDsl
@SinceKotlin("1.2")
interface SimpleEffect {
    @ContractsDsl
    infix fun implies(booleanExpression: Boolean): ConditionalEffect
}


@ContractsDsl
@SinceKotlin("1.2")
interface Returns : SimpleEffect

@ContractsDsl
@SinceKotlin("1.2")
interface ReturnsNotNull : SimpleEffect

@ContractsDsl
@SinceKotlin("1.2")
interface CallsInPlace : SimpleEffect