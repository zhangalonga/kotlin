/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.util

typealias ImmutableMap<K, V> = javaslang.collection.Map<K, V>
typealias ImmutableHashMap<K, V> = javaslang.collection.HashMap<K, V>

fun <K, V> ImmutableMap<K, V>.getOrNull(k: K): V? = get(k)?.getOrElse(null as V?)
