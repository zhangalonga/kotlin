/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package kotlin.coroutines.experimental

import kotlin.coroutines.experimental.intrinsics.*

/**
 * Starts coroutine with receiver type [R] and result type [T].
 * This function creates and start a new, fresh instance of suspendable computation every time it is invoked.
 * The [completion] continuation is invoked when coroutine completes with result or exception.
 */
@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST")
public fun <R, T> (suspend R.() -> T).startCoroutine(
    receiver: R,
    completion: Continuation<T>
) {
    createCoroutineUnchecked(receiver, completion).resume(Unit)
}

/**
 * Starts coroutine without receiver and with result type [T].
 * This function creates and start a new, fresh instance of suspendable computation every time it is invoked.
 * The [completion] continuation is invoked when coroutine completes with result or exception.
 */
@SinceKotlin("1.1")
@Suppress("UNCHECKED_CAST")
public fun <T> (suspend () -> T).startCoroutine(
    completion: Continuation<T>
) {
    createCoroutineUnchecked(completion).resume(Unit)
}

/**
 * Creates a coroutine with receiver type [R] and result type [T].
 * This function creates a new, fresh instance of suspendable computation every time it is invoked.
 *
 * To start executing the created coroutine, invoke `resume(Unit)` on the returned [Continuation] instance.
 * The [completion] continuation is invoked when coroutine completes with result or exception.
 * Repeated invocation of any resume function on the resulting continuation produces [IllegalStateException].
 */
//@SinceKotlin("1.1")
//@Suppress("UNCHECKED_CAST")
//public fun <R, T> (suspend R.() -> T).createCoroutine(
//    receiver: R,
//    completion: Continuation<T>
//): Continuation<Unit> = SafeContinuation(createCoroutineUnchecked(receiver, completion), COROUTINE_SUSPENDED)

/**
 * Creates a coroutine without receiver and with result type [T].
 * This function creates a new, fresh instance of suspendable computation every time it is invoked.
 *
 * To start executing the created coroutine, invoke `resume(Unit)` on the returned [Continuation] instance.
 * The [completion] continuation is invoked when coroutine completes with result or exception.
 * Repeated invocation of any resume function on the resulting continuation produces [IllegalStateException].
 */
//@SinceKotlin("1.1")
//@Suppress("UNCHECKED_CAST")
//public fun <T> (suspend () -> T).createCoroutine(
//    completion: Continuation<T>
//): Continuation<Unit> = SafeContinuation(createCoroutineUnchecked(completion), COROUTINE_SUSPENDED)


public interface CoroutineContext

open class EmptyCoroutineContext : CoroutineContext {
    companion object : EmptyCoroutineContext()
}

public interface Continuation<in T> {
    /**
     * Context of the coroutine that corresponds to this continuation.
     */
    public val context: CoroutineContext

    /**
     * Resumes the execution of the corresponding coroutine passing [value] as the return value of the last suspension point.
     */
    public fun resume(value: T)

    /**
     * Resumes the execution of the corresponding coroutine so that the [exception] is re-thrown right after the
     * last suspension point.
     */
    public fun resumeWithException(exception: Throwable)
}

object EmptyContinuationX: Continuation<Unit> {
    override public val context = EmptyCoroutineContext()

    /**
     * Resumes the execution of the corresponding coroutine passing [value] as the return value of the last suspension point.
     */
    override fun resume(value: Unit) {}

    /**
     * Resumes the execution of the corresponding coroutine so that the [exception] is re-thrown right after the
     * last suspension point.
     */
    override fun resumeWithException(exception: Throwable) {}
}

//@JsName("CoroutineImpl")
internal abstract class CoroutineImpl(private val completion: Continuation<Any?>) : Continuation<Any?> {
    protected var state = 0
    protected var exceptionState = 0
    protected var result: Any? = null
    protected var exception: Throwable? = null
    var label: Any? = null
//    protected var finallyPath: Array<Int>? = null

    public override val context: CoroutineContext = completion.context

//    val facade: Continuation<Any?> = context[ContinuationInterceptor]?.interceptContinuation(this) ?: this

    override fun resume(value: Any?) {
        result = value
        doResumeWrapper()
    }

    override fun resumeWithException(exception: Throwable) {
        state = exceptionState
        this.exception = exception
        doResumeWrapper()
    }

    protected fun doResumeWrapper() {
//        processBareContinuationResume(resultContinuation) { doResume() }
    }

    protected abstract fun doResume(data: Any?, exception: Throwable?): Any?

    open fun create(completion: Continuation<*>): Continuation<Unit> {
        throw IllegalStateException("create(Continuation) has not been overridden")
    }

    open fun create(value: Any?, completion: Continuation<*>): Continuation<Unit> {
        throw IllegalStateException("create(Any?;Continuation) has not been overridden")
    }
}

private val UNDECIDED: Any? = Any()
private val RESUMED: Any? = Any()

private class Fail(val exception: Throwable)
