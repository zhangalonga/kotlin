/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.gradle.util.modify
import org.junit.Assert
import org.junit.Test
import java.util.zip.ZipFile

class NewMultiplatformIT : BaseGradleIT() {


    private fun Project.targetClassesDir(targetName: String, sourceSetName: String = "main") =
        classesDir(sourceSet = "$targetName/$sourceSetName")

    @Test
    fun testLibAndApp() {
        val libProject = Project("sample-lib", GradleVersionRequired.AtLeast("4.8"), "new-mpp-lib-and-app")
        val appProject = Project("sample-app", GradleVersionRequired.AtLeast("4.8"), "new-mpp-lib-and-app")

        with(libProject) {
            build("publish") {
                assertSuccessful()
                assertTasksExecuted(":compileKotlinJvm6", ":compileKotlinNodeJs", ":jvm6Jar", ":nodeJsJar")
                val repoDir = projectDir.resolve("repo")
                val moduleDir = repoDir.resolve("com/example/sample-lib/1.0")
                val jvmJarName = "sample-lib-1.0-jvm6.jar"
                val jsJarName = "sample-lib-1.0-nodeJs.jar"
                listOf(jvmJarName, jsJarName, "sample-lib-1.0.module").forEach {
                    Assert.assertTrue(moduleDir.resolve(it).exists())
                }

                val jvmJarEntries = ZipFile(moduleDir.resolve(jvmJarName)).entries().asSequence().map { it.name }.toSet()
                Assert.assertTrue("com/example/lib/CommonKt.class" in jvmJarEntries)
                Assert.assertTrue("com/example/lib/MainKt.class" in jvmJarEntries)

                val jsJar = ZipFile(moduleDir.resolve(jsJarName))
                val compiledJs = jsJar.getInputStream(jsJar.getEntry("sample-lib.js")).reader().readText()
                Assert.assertTrue("function id(" in compiledJs)
                Assert.assertTrue("function idUsage(" in compiledJs)
                Assert.assertTrue("function expectedFun(" in compiledJs)
                Assert.assertTrue("function main(" in compiledJs)
            }
        }

        val libLocalRepoUri = libProject.projectDir.resolve("repo").toURI()

        with(appProject) {
            setupWorkingDir()
            gradleBuildScript().appendText("\nrepositories { maven { url '$libLocalRepoUri' } }")

            fun CompiledProject.checkAppBuild() {
                assertSuccessful()
                assertTasksExecuted(":compileKotlinJvm6", ":compileKotlinJvm8", ":compileKotlinNodeJs")

                projectDir.resolve(targetClassesDir("jvm6")).run {
                    Assert.assertTrue(resolve("com/example/app/AKt.class").exists())
                    Assert.assertTrue(this.resolve("com/example/app/UseBothIdsKt.class").exists())
                }

                projectDir.resolve(targetClassesDir("jvm8")).run {
                    Assert.assertTrue(resolve("com/example/app/AKt.class").exists())
                    Assert.assertTrue(resolve("com/example/app/UseBothIdsKt.class").exists())
                    Assert.assertTrue(resolve("com/example/app/Jdk8ApiUsageKt.class").exists())
                }

                projectDir.resolve(targetClassesDir("nodeJs")).resolve("sample-app.js").readText().run {
                    Assert.assertTrue(contains("console.info"))
                    Assert.assertTrue(contains("function nodeJsMain("))
                }
            }

            build("assemble") {
                checkAppBuild()
            }

            // Now run again with a project dependency instead of a module one:
            libProject.projectDir.copyRecursively(projectDir.resolve(libProject.projectDir.name))
            projectDir.resolve("settings.gradle").appendText("\ninclude '${libProject.projectDir.name}'")
            gradleBuildScript().modify { it.replace("'com.example:sample-lib:1.0'", "project(':${libProject.projectDir.name}')") }

            build("assemble", "--rerun-tasks") {
                checkAppBuild()
            }
        }
    }

    @Test
    fun testJvmWithJavaEquivalence() = with(Project("sample-lib", GradleVersionRequired.AtLeast("4.8"), "new-mpp-lib-and-app")) {
        lateinit var classesWithoutJava: Set<String>

        fun getFilePathsSet(inDirectory: String): Set<String> {
            val dir = projectDir.resolve(inDirectory)
            return dir.walk().filter { it.isFile }.map { it.relativeTo(dir).path.replace('\\', '/') }.toSet()
        }

        build("assemble") {
            assertSuccessful()
            classesWithoutJava = getFilePathsSet("build/classes")
        }

        gradleBuildScript().modify { it.replace("presets.jvm", "presets.jvmWithJava") }

        projectDir.resolve("src/main/java").apply {
            mkdirs()
            mkdir()
            // Check that Java can access the dependencies (kotlin-stdlib):
            resolve("JavaClassInJava.java").writeText("""
                package com.example.lib;
                import kotlin.sequences.Sequence;
                class JavaClassInJava {
                    Sequence<String> makeSequence() { throw new UnsupportedOperationException(); }
                }
            """.trimIndent())

            // Add a Kotlin source file in the Java source root and check that it is compiled:
            resolve("KotlinClassInJava.kt").writeText("""
                package com.example.lib
                class KotlinClassInJava
            """.trimIndent())
        }

        build("clean", "assemble") {
            assertSuccessful()
            val expectedClasses =
                classesWithoutJava +
                        "kotlin/jvm6/main/com/example/lib/KotlinClassInJava.class" +
                        "java/main/com/example/lib/JavaClassInJava.class"
            val actualClasses = getFilePathsSet("build/classes")
            Assert.assertEquals(expectedClasses, actualClasses)
        }
    }

//    @Test
//    fun testLibWithTests() = with(Project("new-mpp-lib-with-tests", ))
}