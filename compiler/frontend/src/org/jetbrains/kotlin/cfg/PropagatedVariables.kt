/*
 * Copyright 2010-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.cfg

data class LongLongOp(val longV: VarLongValue, val longV1: VarLongValue, val reversed: Boolean = false)
    : Operation(longV, longV1, false) {
    override fun sub(): ArithmeticVar = VarLongValue(longV.longValue - longV1.longValue)
    override fun div(): ArithmeticVar = VarLongValue(longV.longValue / longV1.longValue)
    override fun mul(): ArithmeticVar = VarLongValue(longV.longValue * longV1.longValue)
    override fun sum(): ArithmeticVar = VarLongValue(longV.longValue + longV1.longValue)
    override fun cmp(): VarIntValue = VarIntValue(longV.longValue.compareTo(longV1.longValue))
}

data class ShortShortOp(val shortV: VarShortValue, val shortV1: VarShortValue, val reversed: Boolean = false)
    : Operation(shortV, shortV1, false) {
    override fun sub(): ArithmeticVar = VarIntValue(shortV.shortValue - shortV1.shortValue)
    override fun div(): ArithmeticVar = VarIntValue(shortV.shortValue / shortV1.shortValue)
    override fun mul(): ArithmeticVar = VarIntValue(shortV.shortValue * shortV1.shortValue)
    override fun sum(): ArithmeticVar = VarIntValue(shortV.shortValue + shortV1.shortValue)
    override fun cmp(): VarIntValue = VarIntValue(shortV.shortValue.compareTo(shortV1.shortValue))
}

data class ShortLongOp(val shortV: VarShortValue, val longV: VarLongValue, val reversed: Boolean = false)
    : Operation(shortV, longV, reversed) {
    override fun sub(): ArithmeticVar = when (reversed) {
        false -> VarLongValue(shortV.shortValue - longV.longValue)
        else -> VarLongValue(longV.longValue - shortV.shortValue)
    }

    override fun div(): ArithmeticVar = when (reversed) {
        false -> VarLongValue(shortV.shortValue / longV.longValue)
        else -> VarLongValue(longV.longValue / shortV.shortValue)
    }

    override fun mul(): ArithmeticVar = when (reversed) {
        false -> VarLongValue(shortV.shortValue * longV.longValue)
        else -> VarLongValue(longV.longValue * shortV.shortValue)
    }

    override fun sum(): ArithmeticVar = when (reversed) {
        false -> VarLongValue(shortV.shortValue + longV.longValue)
        else -> VarLongValue(longV.longValue + shortV.shortValue)
    }
    override fun cmp(): VarIntValue =
            VarIntValue(if (reversed)
                            longV.longValue.compareTo(shortV.shortValue)
                        else
                            shortV.shortValue.compareTo(longV.longValue))
}

data class ByteByteOp(val byteV: VarByteValue, val byteV1: VarByteValue, val reversed: Boolean = false)
    : Operation(byteV, byteV1, false) {
    override fun sub(): ArithmeticVar = VarIntValue(byteV.byteValue - byteV1.byteValue)
    override fun div(): ArithmeticVar = VarIntValue(byteV.byteValue / byteV1.byteValue)
    override fun mul(): ArithmeticVar = VarIntValue(byteV.byteValue * byteV1.byteValue)
    override fun sum(): ArithmeticVar = VarIntValue(byteV.byteValue + byteV1.byteValue)
    override fun cmp(): VarIntValue = VarIntValue(byteV.byteValue.compareTo(byteV1.byteValue))
}

data class ByteLongOp(val byteV: VarByteValue, val longV: VarLongValue, val reversed: Boolean = false)
    : Operation(byteV, longV, reversed) {
    override fun sub(): ArithmeticVar = when(reversed) {
        false -> VarLongValue(byteV.byteValue - longV.longValue)
        else -> VarLongValue(longV.longValue - byteV.byteValue)
    }
    override fun div(): ArithmeticVar = when(reversed) {
        false -> VarLongValue(byteV.byteValue / longV.longValue)
        else -> VarLongValue(longV.longValue / byteV.byteValue)
    }
    override fun mul(): ArithmeticVar = when(reversed) {
        false -> VarLongValue(byteV.byteValue * longV.longValue)
        else -> VarLongValue(longV.longValue * byteV.byteValue)
    }
    override fun sum(): ArithmeticVar = when(reversed) {
        false -> VarLongValue(byteV.byteValue + longV.longValue)
        else -> VarLongValue(longV.longValue + byteV.byteValue)
    }
    override fun cmp(): VarIntValue =
            VarIntValue(if (reversed)
                            longV.longValue.compareTo(byteV.byteValue)
                        else
                            byteV.byteValue.compareTo(longV.longValue))
}

data class ByteShortOp(val byteV: VarByteValue, val shortV: VarShortValue, val reversed: Boolean = false)
    : Operation(byteV, shortV, reversed) {
    override fun sub(): ArithmeticVar = when(reversed) {
        false -> VarIntValue(byteV.byteValue - shortV.shortValue)
        else -> VarIntValue(shortV.shortValue - byteV.byteValue)
    }
    override fun div(): ArithmeticVar = when(reversed) {
        false -> VarIntValue(byteV.byteValue / shortV.shortValue)
        else -> VarIntValue(shortV.shortValue / byteV.byteValue)
    }
    override fun mul(): ArithmeticVar = when(reversed) {
        false -> VarIntValue(byteV.byteValue * shortV.shortValue)
        else -> VarIntValue(shortV.shortValue * byteV.byteValue)
    }
    override fun sum(): ArithmeticVar = when(reversed) {
        false -> VarIntValue(byteV.byteValue + shortV.shortValue)
        else -> VarIntValue(shortV.shortValue + byteV.byteValue)
    }
    override fun cmp(): VarIntValue =
            VarIntValue(if (reversed)
                            shortV.shortValue.compareTo(byteV.byteValue)
                        else
                            byteV.byteValue.compareTo(shortV.shortValue))
}

data class DoubleDoubleOp(val doubleV: VarDoubleValue, val doubleV1: VarDoubleValue, val reversed: Boolean = false)
    : Operation(doubleV, doubleV1, false) {
    override fun sub(): ArithmeticVar = VarDoubleValue(doubleV.doubleValue - doubleV1.doubleValue)
    override fun div(): ArithmeticVar = VarDoubleValue(doubleV.doubleValue / doubleV1.doubleValue)
    override fun mul(): ArithmeticVar = VarDoubleValue(doubleV.doubleValue * doubleV1.doubleValue)
    override fun sum(): ArithmeticVar = VarDoubleValue(doubleV.doubleValue + doubleV1.doubleValue)
    override fun cmp(): VarIntValue = VarIntValue(doubleV.doubleValue.compareTo(doubleV1.doubleValue))
}

data class DoubleShortOp(val doubleV: VarDoubleValue, val shortV: VarShortValue, val reversed: Boolean = false)
    : Operation(doubleV, shortV, reversed) {
    override fun sub(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(doubleV.doubleValue - shortV.shortValue)
        else -> VarDoubleValue(shortV.shortValue - doubleV.doubleValue)
    }
    override fun div(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(doubleV.doubleValue / shortV.shortValue)
        else -> VarDoubleValue(shortV.shortValue / doubleV.doubleValue)
    }
    override fun mul(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(doubleV.doubleValue * shortV.shortValue)
        else -> VarDoubleValue(shortV.shortValue * doubleV.doubleValue)
    }
    override fun sum(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(doubleV.doubleValue + shortV.shortValue)
        else -> VarDoubleValue(shortV.shortValue + doubleV.doubleValue)
    }
    override fun cmp(): VarIntValue =
            VarIntValue(if (reversed)
                            shortV.shortValue.compareTo(doubleV.doubleValue)
                        else
                            doubleV.doubleValue.compareTo(shortV.shortValue))
}

data class DoubleLongOp(val doubleV: VarDoubleValue, val longV: VarLongValue, val reversed: Boolean = false)
    : Operation(doubleV, longV, reversed) {
    override fun sub(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(doubleV.doubleValue - longV.longValue)
        else -> VarDoubleValue(longV.longValue - doubleV.doubleValue)
    }
    override fun div(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(doubleV.doubleValue / longV.longValue)
        else -> VarDoubleValue(longV.longValue / doubleV.doubleValue)
    }
    override fun mul(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(doubleV.doubleValue * longV.longValue)
        else -> VarDoubleValue(longV.longValue * doubleV.doubleValue)
    }
    override fun sum(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(doubleV.doubleValue + longV.longValue)
        else -> VarDoubleValue(longV.longValue + doubleV.doubleValue)
    }
    override fun cmp(): VarIntValue =
            VarIntValue(if (reversed)
                            longV.longValue.compareTo(doubleV.doubleValue)
                        else
                            doubleV.doubleValue.compareTo(longV.longValue))
}

data class DoubleByteOp(val doubleV: VarDoubleValue, val byteV: VarByteValue, val reversed: Boolean = false)
    : Operation(doubleV, byteV, reversed) {
    override fun sub(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(doubleV.doubleValue - byteV.byteValue)
        else -> VarDoubleValue(byteV.byteValue - doubleV.doubleValue)
    }
    override fun div(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(doubleV.doubleValue / byteV.byteValue)
        else -> VarDoubleValue(byteV.byteValue / doubleV.doubleValue)
    }
    override fun mul(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(doubleV.doubleValue * byteV.byteValue)
        else -> VarDoubleValue(byteV.byteValue * doubleV.doubleValue)
    }
    override fun sum(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(doubleV.doubleValue + byteV.byteValue)
        else -> VarDoubleValue(byteV.byteValue + doubleV.doubleValue)
    }
    override fun cmp(): VarIntValue =
            VarIntValue(if (reversed)
                            byteV.byteValue.compareTo(doubleV.doubleValue)
                        else
                            doubleV.doubleValue.compareTo(byteV.byteValue))
}

data class FloatByteOp(val floatV: VarFloatValue, val byteV: VarByteValue, val reversed: Boolean = false)
    : Operation(floatV, byteV, reversed) {
    override fun sub(): ArithmeticVar = when(reversed) {
        false -> VarFloatValue(floatV.floatValue - byteV.byteValue)
        else -> VarFloatValue(byteV.byteValue - floatV.floatValue)
    }
    override fun div(): ArithmeticVar = when(reversed) {
        false -> VarFloatValue(floatV.floatValue / byteV.byteValue)
        else -> VarFloatValue(byteV.byteValue / floatV.floatValue)
    }
    override fun mul(): ArithmeticVar = when(reversed) {
        false -> VarFloatValue(floatV.floatValue * byteV.byteValue)
        else -> VarFloatValue(byteV.byteValue * floatV.floatValue)
    }
    override fun sum(): ArithmeticVar = when(reversed) {
        false -> VarFloatValue(floatV.floatValue + byteV.byteValue)
        else -> VarFloatValue(byteV.byteValue + floatV.floatValue)
    }

    override fun cmp(): VarIntValue =
            VarIntValue(if (reversed)
                            byteV.byteValue.compareTo(floatV.floatValue)
                        else
                            floatV.floatValue.compareTo(byteV.byteValue))
}

data class FloatShortOp(val floatV: VarFloatValue, val shortV: VarShortValue, val reversed: Boolean = false)
    : Operation(floatV, shortV, reversed) {
    override fun sub(): ArithmeticVar = when(reversed) {
        false -> VarFloatValue(floatV.floatValue - shortV.shortValue)
        else -> VarFloatValue(shortV.shortValue - floatV.floatValue)
    }

    override fun div(): ArithmeticVar = when(reversed) {
        false -> VarFloatValue(floatV.floatValue / shortV.shortValue)
        else -> VarFloatValue(shortV.shortValue / floatV.floatValue)
    }
    override fun mul(): ArithmeticVar = when(reversed) {
        false -> VarFloatValue(floatV.floatValue * shortV.shortValue)
        else -> VarFloatValue(shortV.shortValue * floatV.floatValue)
    }
    override fun sum(): ArithmeticVar = when(reversed) {
        false -> VarFloatValue(floatV.floatValue + shortV.shortValue)
        else -> VarFloatValue(shortV.shortValue + floatV.floatValue)
    }

    override fun cmp(): VarIntValue =
            VarIntValue(if (reversed)
                            shortV.shortValue.compareTo(floatV.floatValue)
                        else
                            floatV.floatValue.compareTo(shortV.shortValue))
}

data class FloatFloatOp(val floatV: VarFloatValue, val floatV1: VarFloatValue, val reversed: Boolean = false)
    : Operation(floatV, floatV1, false) {
    override fun sub(): ArithmeticVar = VarFloatValue(floatV.floatValue - floatV1.floatValue)
    override fun div(): ArithmeticVar = VarFloatValue(floatV.floatValue / floatV1.floatValue)
    override fun mul(): ArithmeticVar = VarFloatValue(floatV.floatValue * floatV1.floatValue)
    override fun sum(): ArithmeticVar = VarFloatValue(floatV.floatValue + floatV1.floatValue)

    override fun cmp(): VarIntValue = VarIntValue(floatV.floatValue.compareTo(floatV1.floatValue))
}

data class FloatDoubleOp(val floatV: VarFloatValue, val doubleV: VarDoubleValue, val reversed: Boolean = false)
    : Operation(floatV, doubleV, reversed) {
    override fun sub(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(floatV.floatValue - doubleV.doubleValue)
        else -> VarDoubleValue(doubleV.doubleValue - floatV.floatValue)
    }

    override fun div(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(floatV.floatValue / doubleV.doubleValue)
        else -> VarDoubleValue(doubleV.doubleValue / floatV.floatValue)
    }
    override fun mul(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(floatV.floatValue * doubleV.doubleValue)
        else -> VarDoubleValue(doubleV.doubleValue * floatV.floatValue)
    }
    override fun sum(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(floatV.floatValue + doubleV.doubleValue)
        else -> VarDoubleValue(doubleV.doubleValue + floatV.floatValue)
    }

    override fun cmp(): VarIntValue =
            VarIntValue(if (reversed)
                            doubleV.doubleValue.compareTo(floatV.floatValue)
                        else
                            floatV.floatValue.compareTo(doubleV.doubleValue))
}

data class FloatLongOp(val floatV: VarFloatValue, val longV: VarLongValue, val reversed: Boolean = false)
    : Operation(floatV, longV, reversed) {
    override fun sub(): ArithmeticVar = when(reversed) {
        false -> VarFloatValue(floatV.floatValue - longV.longValue)
        else -> VarFloatValue(longV.longValue - floatV.floatValue)
    }

    override fun div(): ArithmeticVar = when(reversed) {
        false -> VarFloatValue(floatV.floatValue / longV.longValue)
        else -> VarFloatValue(longV.longValue / floatV.floatValue)
    }
    override fun mul(): ArithmeticVar = when(reversed) {
        false -> VarFloatValue(floatV.floatValue * longV.longValue)
        else -> VarFloatValue(longV.longValue * floatV.floatValue)
    }
    override fun sum(): ArithmeticVar = when(reversed) {
        false -> VarFloatValue(floatV.floatValue + longV.longValue)
        else -> VarFloatValue(longV.longValue + floatV.floatValue)
    }

    override fun cmp(): VarIntValue =
            VarIntValue(if (reversed)
                            longV.longValue.compareTo(floatV.floatValue)
                        else
                            floatV.floatValue.compareTo(longV.longValue))
}

data class IntLongOp(val intV: VarIntValue, val longV: VarLongValue, val reversed: Boolean = false)
    : Operation(intV, longV, reversed) {
    override fun sub(): ArithmeticVar = when(reversed) {
        false -> VarLongValue(intV.intValue - longV.longValue)
        else -> VarLongValue(longV.longValue - intV.intValue)
    }

    override fun div(): ArithmeticVar = when(reversed) {
        false -> VarLongValue(intV.intValue / longV.longValue)
        else -> VarLongValue(longV.longValue / intV.intValue)
    }
    override fun mul(): ArithmeticVar = when(reversed) {
        false -> VarLongValue(intV.intValue * longV.longValue)
        else -> VarLongValue(longV.longValue * intV.intValue)
    }
    override fun sum(): ArithmeticVar = when(reversed) {
        false -> VarLongValue(intV.intValue + longV.longValue)
        else -> VarLongValue(longV.longValue + intV.intValue)
    }

    override fun cmp(): VarIntValue =
            VarIntValue(if (reversed)
                            longV.longValue.compareTo(intV.intValue)
                        else
                            intV.intValue.compareTo(longV.longValue))
}

data class IntShortOp(val intV: VarIntValue, val shortV: VarShortValue, val reversed: Boolean = false)
    : Operation(intV, shortV, reversed) {
    override fun sub(): ArithmeticVar = when(reversed) {
        false -> VarIntValue(intV.intValue - shortV.shortValue)
        else -> VarIntValue(shortV.shortValue - intV.intValue)
    }

    override fun div(): ArithmeticVar = when(reversed) {
        false -> VarIntValue(intV.intValue / shortV.shortValue)
        else -> VarIntValue(shortV.shortValue / intV.intValue)
    }
    override fun mul(): ArithmeticVar = when(reversed) {
        false -> VarIntValue(intV.intValue * shortV.shortValue)
        else -> VarIntValue(shortV.shortValue * intV.intValue)
    }
    override fun sum(): ArithmeticVar = when(reversed) {
        false -> VarIntValue(intV.intValue + shortV.shortValue)
        else -> VarIntValue(shortV.shortValue + intV.intValue)
    }

    override fun cmp(): VarIntValue =
            VarIntValue(if (reversed)
                            shortV.shortValue.compareTo(intV.intValue)
                        else
                            intV.intValue.compareTo(shortV.shortValue))
}

data class IntByteOp(val intV: VarIntValue, val byteV: VarByteValue, val reversed: Boolean = false)
    : Operation(intV, byteV, reversed) {
    override fun sub(): ArithmeticVar = when(reversed) {
        false -> VarIntValue(intV.intValue - byteV.byteValue)
        else -> VarIntValue(byteV.byteValue - intV.intValue)
    }

    override fun div(): ArithmeticVar = when(reversed) {
        false -> VarIntValue(intV.intValue / byteV.byteValue)
        else -> VarIntValue(byteV.byteValue / intV.intValue)
    }
    override fun mul(): ArithmeticVar = when(reversed) {
        false -> VarIntValue(intV.intValue * byteV.byteValue)
        else -> VarIntValue(byteV.byteValue * intV.intValue)
    }
    override fun sum(): ArithmeticVar = when(reversed) {
        false -> VarIntValue(intV.intValue + byteV.byteValue)
        else -> VarIntValue(byteV.byteValue + intV.intValue)
    }

    override fun cmp(): VarIntValue =
            VarIntValue(if (reversed)
                            byteV.byteValue.compareTo(intV.intValue)
                        else
                            intV.intValue.compareTo(byteV.byteValue))
}

data class IntIntOp(val intV1: VarIntValue, val intV2: VarIntValue)
    : Operation(intV1, intV2, false) {
    override fun sub(): ArithmeticVar = VarIntValue(intV1.intValue - intV2.intValue)
    override fun div(): ArithmeticVar = VarIntValue(intV1.intValue / intV2.intValue)
    override fun mul(): ArithmeticVar = VarIntValue(intV1.intValue * intV2.intValue)
    override fun sum(): ArithmeticVar = VarIntValue(intV1.intValue + intV2.intValue)

    override fun cmp(): VarIntValue =
            VarIntValue(intV1.intValue.compareTo(intV2.intValue))
}

data class IntDoubleOp(val intV: VarIntValue, val doubleV: VarDoubleValue, val reversed: Boolean = false)
    : Operation(intV, doubleV, reversed) {
    override fun sub(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(intV.intValue - doubleV.doubleValue)
        else -> VarDoubleValue(doubleV.doubleValue - intV.intValue)
    }

    override fun div(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(intV.intValue / doubleV.doubleValue)
        else -> VarDoubleValue(doubleV.doubleValue / intV.intValue)
    }

    override fun mul(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(intV.intValue * doubleV.doubleValue)
        else -> VarDoubleValue(doubleV.doubleValue * intV.intValue)
    }
    override fun sum(): ArithmeticVar = when(reversed) {
        false -> VarDoubleValue(intV.intValue + doubleV.doubleValue)
        else -> VarDoubleValue(doubleV.doubleValue + intV.intValue)
    }

    override fun cmp(): VarIntValue =
            VarIntValue(if (reversed)
                            doubleV.doubleValue.compareTo(intV.intValue)
                        else
                            intV.intValue.compareTo(doubleV.doubleValue))
}

data class IntFloatOp(val intV: VarIntValue, val floatV: VarFloatValue, val reversed: Boolean = false)
    : Operation(intV, floatV, reversed) {
    override fun sub(): ArithmeticVar = when(reversed) {
        false -> VarFloatValue(intV.intValue - floatV.floatValue)
        else -> VarFloatValue(floatV.floatValue - intV.intValue)
    }

    override fun div(): ArithmeticVar = when(reversed) {
        false -> VarFloatValue(intV.intValue / floatV.floatValue)
        else -> VarFloatValue(floatV.floatValue / intV.intValue)
    }

    override fun mul(): ArithmeticVar = when(reversed) {
        false -> VarFloatValue(intV.intValue * floatV.floatValue)
        else -> VarFloatValue(floatV.floatValue * intV.intValue)
    }
    override fun sum(): ArithmeticVar = when(reversed) {
        false -> VarFloatValue(intV.intValue + floatV.floatValue)
        else -> VarFloatValue(floatV.floatValue + intV.intValue)
    }

    override fun cmp(): VarIntValue =
            VarIntValue(if (reversed)
                floatV.floatValue.compareTo(intV.intValue)
            else
                intV.intValue.compareTo(floatV.floatValue))
}

abstract class Operation(val v1: ArithmeticVar, val v2: ArithmeticVar, reversed: Boolean) {
    abstract fun sum(): ArithmeticVar
    abstract fun sub(): ArithmeticVar
    abstract fun div(): ArithmeticVar
    abstract fun mul(): ArithmeticVar
    abstract fun cmp(): VarIntValue
}


data class VarShortValue(val shortValue: Short) : ArithmeticVar(shortValue.toString(), PropagatedTypes.SHORT) {
    override fun sub(aV: ArithmeticVar): ArithmeticVar = createOp(aV).sub()
    override fun div(aV: ArithmeticVar): ArithmeticVar = createOp(aV).div()
    override fun mul(aV: ArithmeticVar): ArithmeticVar = createOp(aV).mul()
    override fun sum(aV: ArithmeticVar): ArithmeticVar = createOp(aV).sum()
    override fun cmp(aV: ArithmeticVar): VarIntValue = createOp(aV).cmp()

    override fun createOp(aV: ArithmeticVar): Operation {
        return when (aV) {
            is VarIntValue -> IntShortOp(aV, this, true)
            is VarLongValue -> ShortLongOp(this, aV)
            is VarByteValue -> ByteShortOp(aV, this, true)
            is VarDoubleValue -> DoubleShortOp(aV, this, true)
            is VarFloatValue -> FloatShortOp(aV, this, true)
            is VarShortValue -> ShortShortOp(this, aV)
        }
    }
}

data class VarByteValue(val byteValue: Byte) : ArithmeticVar(byteValue.toString(), PropagatedTypes.BYTE) {
    override fun sub(aV: ArithmeticVar): ArithmeticVar = createOp(aV).sub()
    override fun div(aV: ArithmeticVar): ArithmeticVar = createOp(aV).div()
    override fun mul(aV: ArithmeticVar): ArithmeticVar = createOp(aV).mul()
    override fun sum(aV: ArithmeticVar): ArithmeticVar = createOp(aV).sum()
    override fun cmp(aV: ArithmeticVar): VarIntValue = createOp(aV).cmp()

    override fun createOp(aV: ArithmeticVar): Operation {
        return when (aV) {
            is VarIntValue -> IntByteOp(aV, this, true)
            is VarLongValue -> ByteLongOp(this, aV)
            is VarByteValue -> ByteByteOp(this, aV)
            is VarDoubleValue -> DoubleByteOp(aV, this, true)
            is VarFloatValue -> FloatByteOp(aV, this, true)
            is VarShortValue -> ByteShortOp(this, aV)
        }
    }
}

data class VarLongValue(val longValue: Long) : ArithmeticVar(longValue.toString(), PropagatedTypes.LONG) {
    override fun sub(aV: ArithmeticVar): ArithmeticVar = createOp(aV).sub()
    override fun div(aV: ArithmeticVar): ArithmeticVar = createOp(aV).div()
    override fun mul(aV: ArithmeticVar): ArithmeticVar = createOp(aV).mul()
    override fun sum(aV: ArithmeticVar): ArithmeticVar = createOp(aV).sum()
    override fun cmp(aV: ArithmeticVar): VarIntValue = createOp(aV).cmp()

    override fun createOp(aV: ArithmeticVar): Operation {
        return when (aV) {
            is VarIntValue -> IntLongOp(aV, this, true)
            is VarLongValue -> LongLongOp(this, aV)
            is VarByteValue -> ByteLongOp(aV, this, true)
            is VarDoubleValue -> DoubleLongOp(aV, this, true)
            is VarFloatValue -> FloatLongOp(aV, this, true)
            is VarShortValue -> ShortLongOp(aV,this, true)
        }
    }
}


data class VarFloatValue(val floatValue: Float) : ArithmeticVar(floatValue.toString(), PropagatedTypes.FLOAT) {
    override fun sub(aV: ArithmeticVar): ArithmeticVar = createOp(aV).sub()
    override fun div(aV: ArithmeticVar): ArithmeticVar = createOp(aV).div()
    override fun mul(aV: ArithmeticVar): ArithmeticVar = createOp(aV).mul()
    override fun sum(aV: ArithmeticVar): ArithmeticVar = createOp(aV).sum()
    override fun cmp(aV: ArithmeticVar): VarIntValue = createOp(aV).cmp()

    override fun createOp(aV: ArithmeticVar): Operation {
        return when (aV) {
            is VarIntValue -> IntFloatOp(aV, this, true)
            is VarLongValue -> FloatLongOp(this, aV)
            is VarByteValue -> FloatByteOp(this, aV)
            is VarDoubleValue -> FloatDoubleOp(this, aV)
            is VarFloatValue -> FloatFloatOp(this, aV)
            is VarShortValue -> FloatShortOp(this, aV)
        }
    }
}

data class VarDoubleValue(val doubleValue: Double) : ArithmeticVar(doubleValue.toString(), PropagatedTypes.DOUBLE) {
    override fun sub(aV: ArithmeticVar): ArithmeticVar = createOp(aV).sub()
    override fun div(aV: ArithmeticVar): ArithmeticVar = createOp(aV).div()
    override fun mul(aV: ArithmeticVar): ArithmeticVar = createOp(aV).mul()
    override fun sum(aV: ArithmeticVar): ArithmeticVar = createOp(aV).sum()
    override fun cmp(aV: ArithmeticVar): VarIntValue = createOp(aV).cmp()

    override fun createOp(aV: ArithmeticVar): Operation {
        return when (aV) {
            is VarIntValue -> IntDoubleOp(aV, this, true)
            is VarLongValue -> DoubleLongOp(this, aV)
            is VarByteValue -> DoubleByteOp(this, aV)
            is VarDoubleValue -> DoubleDoubleOp(this, aV)
            is VarFloatValue -> FloatDoubleOp(aV, this, true)
            is VarShortValue -> DoubleShortOp(this, aV)
        }
    }
}

data class VarIntValue(val intValue: Int) : ArithmeticVar(intValue.toString(), PropagatedTypes.INT) {
    override fun sub(aV: ArithmeticVar): ArithmeticVar = createOp(aV).sub()
    override fun div(aV: ArithmeticVar): ArithmeticVar = createOp(aV).div()
    override fun mul(aV: ArithmeticVar): ArithmeticVar = createOp(aV).mul()
    override fun sum(aV: ArithmeticVar): ArithmeticVar = createOp(aV).sum()
    override fun cmp(aV: ArithmeticVar): VarIntValue = createOp(aV).cmp()

    override fun createOp(aV: ArithmeticVar): Operation {
        return when (aV) {
            is VarIntValue -> IntIntOp(this, aV)
            is VarLongValue -> IntLongOp(this, aV)
            is VarByteValue -> IntByteOp(this, aV)
            is VarDoubleValue -> IntDoubleOp(this, aV)
            is VarFloatValue -> IntFloatOp(this, aV)
            is VarShortValue -> IntShortOp(this, aV)
        }
    }
}

sealed class ArithmeticVar(val value: String, val type: PropagatedTypes): PropagatedVariable(value, type) {
    abstract fun sum(aV: ArithmeticVar): ArithmeticVar
    abstract fun sub(aV: ArithmeticVar): ArithmeticVar
    abstract fun div(aV: ArithmeticVar): ArithmeticVar
    abstract fun mul(aV: ArithmeticVar): ArithmeticVar
    abstract fun createOp(aV: ArithmeticVar): Operation
    abstract fun cmp(aV: ArithmeticVar): VarIntValue
    fun eq(aV: ArithmeticVar): BooleanVar = BooleanVar(this.value == aV.value)
}

data class BooleanVar(val value: Boolean): PropagatedVariable(value.toString(), PropagatedTypes.BOOLEAN) {
    fun and(booleanVar: BooleanVar): BooleanVar = BooleanVar(this.value && booleanVar.value)
    fun or(booleanVar: BooleanVar): BooleanVar = BooleanVar(this.value || booleanVar.value)
    fun not() = BooleanVar(!this.value)
}

data class StringVar(val value: String): PropagatedVariable(value, PropagatedTypes.STRING) {
    fun sum(v: PropagatedVariable): StringVar = StringVar(value + v.pValue)
}

sealed class PropagatedVariable(val pValue: String, val pType: PropagatedTypes)

