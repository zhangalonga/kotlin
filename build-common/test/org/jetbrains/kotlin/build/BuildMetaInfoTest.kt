/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build

import junit.framework.TestCase
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BuildMetaInfoTest : TestCase() {
    @Test
    fun testJvmSerialization() {
        val args = K2JVMCompilerArguments()
        val info = JvmBuildMetaInfo.create(args)
        val actual = JvmBuildMetaInfo.serializeToString(info)
        val expectedKeys = listOf(
            "apiVersionString",
            "bytecodeVersionMajor",
            "bytecodeVersionMinor",
            "bytecodeVersionPatch",
            "compilerBuildVersion",
            "coroutinesEnable",
            "coroutinesError",
            "coroutinesVersion",
            "coroutinesWarn",
            "isEAP",
            "languageVersionString",
            "metadataVersionMajor",
            "metadataVersionMinor",
            "metadataVersionPatch",
            "multiplatformEnable",
            "multiplatformVersion",
            "ownVersion"
        )
        assertEquals(expectedKeys, actual.split("\r\n", "\n").map { line -> line.split("=").first() })
    }

    @Test
    fun testJvmSerializationDeserialization() {
        val args = K2JVMCompilerArguments()
        val info = JvmBuildMetaInfo.create(args)
        val serialized = JvmBuildMetaInfo.serializeToString(info)
        val deserialized = JvmBuildMetaInfo.deserializeFromString(serialized)
        assertEquals(info, deserialized)
    }

    @Test
    fun testJsSerializationDeserialization() {
        val args = K2JVMCompilerArguments()
        val info = JvmBuildMetaInfo.create(args)
        val serialized = JvmBuildMetaInfo.serializeToString(info)
        val deserialized = JvmBuildMetaInfo.deserializeFromString(serialized)
        assertEquals(info, deserialized)
    }

    @Test
    fun testJvmEquals() {
        val args1 = K2JVMCompilerArguments()
        args1.multiPlatform = true
        val info1 = JvmBuildMetaInfo.create(args1)

        val args2 = K2JVMCompilerArguments()
        args2.multiPlatform = false
        val info2 = JvmBuildMetaInfo.create(args2)

        assertNotEquals(info1, info2)
        assertEquals(info1, info2.copy(multiplatformEnable = true))
    }
}
