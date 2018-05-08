/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package kotlin.coroutines.jvm.internal

import java.lang.IllegalStateException
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

    @JvmField
    var objects: Array<Any?>
    @JvmField
    var objectsTop: Int = 0
    @JvmField
    protected var longs: LongArray = longArrayOf(0, 0, 0, 0, 0)
    @JvmField
    protected var longsTop: Int = 0

    init {
        val comp = completion
        if (comp is CoroutineImpl) {
            objects = comp.objects
            objectsTop = comp.objectsTop
        } else {
            objects = arrayOfNulls(0)
            objectsTop = 0
        }
    }

    fun reallocObjects(n: Int) {
        if (objectsTop + n >= objects.size) {
            val newObjects = arrayOfNulls<Any?>((objectsTop + n) * 2)
            System.arraycopy(objects, 0, newObjects, 0, objects.size)
            objects = newObjects
        }
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
