/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package kotlin.coroutines.experimental.intrinsics

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.EmptyContinuationX

@SinceKotlin("1.1")
@kotlin.internal.InlineOnly
@Suppress("UNUSED_PARAMETER")
public suspend inline fun <T> suspendCoroutineOrReturn(crossinline block: (Continuation<T>) -> Any?): T =
    suspendCoroutineUninterceptedOrReturn { cont -> block(cont.intercepted()) }

/**
 * Obtains the current continuation instance inside suspend functions and either suspends
 * currently running coroutine or returns result immediately without suspension.
 *
 * Unlike [suspendCoroutineOrReturn] it does not intercept continuation.
 */
@SinceKotlin("1.2")
@kotlin.internal.InlineOnly
public suspend inline fun <T> suspendCoroutineUninterceptedOrReturn(crossinline block: (Continuation<T>) -> Any?): T =
    throw Exception("Implementation of suspendCoroutineUninterceptedOrReturn is intrinsic")

/**
 * Intercept continuation with [ContinuationInterceptor].
 */
@SinceKotlin("1.2")
@kotlin.internal.InlineOnly
public inline fun <T> Continuation<T>.intercepted(): Continuation<T> =
    throw Exception("Implementation of intercepted is intrinsic")

/**
 * This value is used as a return value of [suspendCoroutineOrReturn] `block` argument to state that
 * the execution was suspended and will not return any result immediately.
 */
@SinceKotlin("1.1")
public val COROUTINE_SUSPENDED: Any = Any()


@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST")
@kotlin.internal.InlineOnly
public inline fun <T> (suspend () -> T).startCoroutineUninterceptedOrReturn(
    completion: Continuation<T>
): Any? = null//this.asDynamic()(completion, false)

@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST")
@kotlin.internal.InlineOnly
public inline fun <R, T> (suspend R.() -> T).startCoroutineUninterceptedOrReturn(
    receiver: R,
    completion: Continuation<T>
): Any? = null //this.asDynamic()(receiver, completion, false)

@SinceKotlin("1.1")
public fun <R, T> (suspend R.() -> T).createCoroutineUnchecked(
    receiver: R,
    completion: Continuation<T>
): Continuation<Unit> = EmptyContinuationX //this.asDynamic()(receiver, completion, true).facade

@SinceKotlin("1.1")
public fun <T> (suspend () -> T).createCoroutineUnchecked(
    completion: Continuation<T>
): Continuation<Unit> = EmptyContinuationX //this.asDynamic()(completion, true).facade