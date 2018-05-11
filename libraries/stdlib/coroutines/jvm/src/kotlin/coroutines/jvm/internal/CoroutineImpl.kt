/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package kotlin.coroutines.jvm.internal

import java.lang.IllegalStateException
import java.util.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.processBareContinuationResume
import kotlin.jvm.internal.Lambda

/**
 * @suppress
 */
@SinceKotlin("1.3")
public abstract class CoroutineImpl(
    arity: Int,
    @JvmField
    protected var completion: Continuation<Any?>?
) : Lambda(arity), Continuation<Any?> {

    // label == -1 when coroutine cannot be started (it is just a factory object) or has already finished execution
    // label == 0 in initial part of the coroutine
    @JvmField
    protected var label: Int = if (completion != null) 0 else -1

    private var objects: Array<Any?>
    private var objectsTop: IntArray
    private var ints: IntArray
    private var intsTop: IntArray

    init {
        val comp = completion
        if (comp is CoroutineImpl) {
            objects = comp.objects
            objectsTop = comp.objectsTop
            ints = comp.ints
            intsTop = comp.intsTop
        } else {
            objects = arrayOfNulls(5)
            objectsTop = intArrayOf(0)
            ints = intArrayOf(0, 0, 0, 0, 0)
            intsTop = intArrayOf(0)
        }
    }

    fun pushObject(o: Any?) {
        if (objectsTop[0] >= objects.size) {
            objects = Arrays.copyOf(objects, objectsTop[0] * 2)
        }
        objects[objectsTop[0]++] = o
    }

    fun popObject(): Any? {
        return objects[--objectsTop[0]]
    }

    fun pushInt(i: Int) {
        if (intsTop[0] >= ints.size) {
            ints = Arrays.copyOf(ints, intsTop[0] * 2)
        }
        ints[intsTop[0]++] = i
    }

    fun popInt(): Int {
        return ints[--intsTop[0]]
    }

    private val _context: CoroutineContext? = completion?.context

    override val context: CoroutineContext
        get() = _context!!

    private var _facade: Continuation<Any?>? = null

    val facade: Continuation<Any?> get() {
        if (_facade == null) _facade = interceptContinuationIfNeeded(_context!!, this)
        return _facade!!
    }

    override fun resume(value: Any?) {
        processBareContinuationResume(completion!!) {
            doResume(value, null)
        }
    }

    override fun resumeWithException(exception: Throwable) {
        processBareContinuationResume(completion!!) {
            doResume(null, exception)
        }
    }

    protected abstract fun doResume(data: Any?, exception: Throwable?): Any?

    open fun create(completion: Continuation<*>): Continuation<Unit> {
        throw IllegalStateException("create(Continuation) has not been overridden")
    }

    open fun create(value: Any?, completion: Continuation<*>): Continuation<Unit> {
        throw IllegalStateException("create(Any?;Continuation) has not been overridden")
    }
}
