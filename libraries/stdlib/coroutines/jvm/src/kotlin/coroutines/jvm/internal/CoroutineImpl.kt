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

    fun pushObjects(o: Any?) {
        val top = objectsTop[0]
        reallocObjectsIfNeeded(top + 1)
        objects[top] = o
        objectsTop[0] = top + 1
    }

    fun pushObjects(a: Any?, b: Any?) {
        val top = objectsTop[0]
        reallocObjectsIfNeeded(top + 2)
        objects[top] = a
        objects[top + 1] = b
        objectsTop[0] = top + 2
    }

    fun pushObjects(a: Any?, b: Any?, c: Any?) {
        val top = objectsTop[0]
        reallocObjectsIfNeeded(top + 3)
        objects[top] = a
        objects[top + 1] = b
        objects[top + 2] = c
        objectsTop[0] = top + 3
    }

    private fun reallocObjectsIfNeeded(i: Int) {
        if (i >= objects.size) {
            objects = Arrays.copyOf(objects, i * 2)
        }
    }

    // TODO: add multiple methods: escape analysis will take care of allocations
    fun popObject(): Any? {
        return objects[--objectsTop[0]]
    }

    fun dropObjects(i: Int) {
        objectsTop[0] -= i
    }

    fun pushInts(i: Int) {
        val top = intsTop[0]
        reallocIntsIfNeeded(top + 1)
        ints[top] = i
        intsTop[0] = top + 1
    }

    fun pushInts(a: Int, b: Int) {
        val top = intsTop[0]
        reallocIntsIfNeeded(top + 2)
        ints[top] = a
        ints[top + 1] = b
        intsTop[0] = top + 2
    }

    fun pushInts(a: Int, b: Int, c: Int) {
        val top = intsTop[0]
        reallocIntsIfNeeded(top + 3)
        ints[top] = a
        ints[top + 1] = b
        ints[top + 2] = c
        intsTop[0] = top + 3
    }

    private fun reallocIntsIfNeeded(i: Int) {
        if (i >= ints.size) {
            ints = Arrays.copyOf(ints, i * 2)
        }
    }

    fun popInt(): Int {
        return ints[--intsTop[0]]
    }

    fun dropInts(i: Int) {
        intsTop[0] -= i
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
