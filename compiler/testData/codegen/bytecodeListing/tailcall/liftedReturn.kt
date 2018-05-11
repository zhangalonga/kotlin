// COMMON_COROUTINES_TEST
abstract class B {
    protected abstract fun ready(): Boolean
    protected abstract suspend fun slow(): Boolean

    suspend fun notLifted(): Boolean {
        if (ready()) {
            return true
        } else {
            return slow()
        }
    }

    suspend fun lifted(): Boolean {
        return if (ready()) {
            true
        } else {
            slow()
        }
    }
}

abstract class C {
    protected abstract fun ready(): Boolean
    protected abstract suspend fun slow(): Char

    suspend fun notLifted(): Char {
        if (ready()) {
            return 'a'
        } else {
            return slow()
        }
    }

    suspend fun lifted(): Char {
        return if (ready()) {
            'a'
        } else {
            slow()
        }
    }
}

abstract class By {
    protected abstract fun ready(): Boolean
    protected abstract suspend fun slow(): Byte

    suspend fun notLifted(): Byte {
        if (ready()) {
            return 0
        } else {
            return slow()
        }
    }

    suspend fun lifted(): Byte {
        return if (ready()) {
            0
        } else {
            slow()
        }
    }
}

abstract class S {
    protected abstract fun ready(): Boolean
    protected abstract suspend fun slow(): Short

    suspend fun notLifted(): Short {
        if (ready()) {
            return 0
        } else {
            return slow()
        }
    }

    suspend fun lifted(): Short {
        return if (ready()) {
            0
        } else {
            slow()
        }
    }
}

abstract class I {
    protected abstract fun ready(): Boolean
    protected abstract suspend fun slow(): Int

    suspend fun notLifted(): Int {
        if (ready()) {
            return 0
        } else {
            return slow()
        }
    }

    suspend fun lifted(): Int {
        return if (ready()) {
            0
        } else {
            slow()
        }
    }
}

abstract class L {
    protected abstract fun ready(): Boolean
    protected abstract suspend fun slow(): Long

    suspend fun notLifted(): Long {
        if (ready()) {
            return 0
        } else {
            return slow()
        }
    }

    suspend fun lifted(): Long {
        return if (ready()) {
            0
        } else {
            slow()
        }
    }
}

abstract class F {
    protected abstract fun ready(): Boolean
    protected abstract suspend fun slow(): Float

    suspend fun notLifted(): Float {
        if (ready()) {
            return .0f
        } else {
            return slow()
        }
    }

    suspend fun lifted(): Float {
        return if (ready()) {
            .0f
        } else {
            slow()
        }
    }
}

abstract class D {
    protected abstract fun ready(): Boolean
    protected abstract suspend fun slow(): Double

    suspend fun notLifted(): Double {
        if (ready()) {
            return .0
        } else {
            return slow()
        }
    }

    suspend fun lifted(): Double {
        return if (ready()) {
            .0
        } else {
            slow()
        }
    }
}