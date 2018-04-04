/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.util

import org.jetbrains.kotlin.name.Name

object OperatorNameConventions {
    @JvmField val GET_VALUE: Name = Name.identifier("getValue")
    @JvmField val SET_VALUE: Name = Name.identifier("setValue")
    @JvmField val PROVIDE_DELEGATE: Name = Name.identifier("provideDelegate")

    @JvmField val EQUALS: Name = Name.identifier("equals")
    @JvmField val COMPARE_TO: Name = Name.identifier("compareTo")
    @JvmField val CONTAINS: Name = Name.identifier("contains")
    @JvmField val INVOKE: Name = Name.identifier("invoke")
    @JvmField val ITERATOR: Name = Name.identifier("iterator")
    @JvmField val GET: Name = Name.identifier("get")
    @JvmField val SET: Name = Name.identifier("set")
    @JvmField val NEXT: Name = Name.identifier("next")
    @JvmField val HAS_NEXT: Name = Name.identifier("hasNext")

    @JvmField val COMPONENT_REGEX: Regex = Regex("component\\d+")

    @JvmField val AND: Name = Name.identifier("and")
    @JvmField val OR: Name = Name.identifier("or")

    @JvmField val INC: Name = Name.identifier("inc")
    @JvmField val DEC: Name = Name.identifier("dec")
    @JvmField val PLUS: Name = Name.identifier("plus")
    @JvmField val MINUS: Name = Name.identifier("minus")
    @JvmField val NOT: Name = Name.identifier("not")

    @JvmField val UNARY_MINUS: Name = Name.identifier("unaryMinus")
    @JvmField val UNARY_PLUS: Name = Name.identifier("unaryPlus")

    @JvmField val TIMES: Name = Name.identifier("times")
    @JvmField val DIV: Name = Name.identifier("div")
    @JvmField val MOD: Name = Name.identifier("mod")
    @JvmField val REM: Name = Name.identifier("rem")
    @JvmField val RANGE_TO: Name = Name.identifier("rangeTo")

    @JvmField val TIMES_ASSIGN: Name = Name.identifier("timesAssign")
    @JvmField val DIV_ASSIGN: Name = Name.identifier("divAssign")
    @JvmField val MOD_ASSIGN: Name = Name.identifier("modAssign")
    @JvmField val REM_ASSIGN: Name = Name.identifier("remAssign")
    @JvmField val PLUS_ASSIGN: Name = Name.identifier("plusAssign")
    @JvmField val MINUS_ASSIGN: Name = Name.identifier("minusAssign")

    // If you add new unary, binary or assignment operators, add it to OperatorConventions as well

    @JvmField
    val UNARY_OPERATION_NAMES: Set<Name> = setOf(INC, DEC, UNARY_PLUS, UNARY_MINUS, NOT)

    @JvmField
    internal val SIMPLE_UNARY_OPERATION_NAMES = setOf(UNARY_PLUS, UNARY_MINUS, NOT)

    @JvmField
    val BINARY_OPERATION_NAMES: Set<Name> = setOf(TIMES, PLUS, MINUS, DIV, MOD, REM, RANGE_TO)

    @JvmField
    internal val ASSIGNMENT_OPERATIONS = setOf(TIMES_ASSIGN, DIV_ASSIGN, MOD_ASSIGN, REM_ASSIGN, PLUS_ASSIGN, MINUS_ASSIGN)
}
