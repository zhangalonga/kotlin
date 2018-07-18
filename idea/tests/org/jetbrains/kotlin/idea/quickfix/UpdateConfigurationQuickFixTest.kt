/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.facet.FacetManager
import com.intellij.facet.impl.FacetUtil
import com.intellij.openapi.roots.ModuleRootModificationUtil.updateModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.configuration.KotlinJavaModuleConfigurator
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.facet.getRuntimeLibraryVersion
import org.jetbrains.kotlin.idea.project.getLanguageVersionSettings
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.configureKotlinFacet
import org.jetbrains.kotlin.idea.versions.bundledRuntimeVersion
import java.io.File

class UpdateConfigurationQuickFixTest : LightPlatformCodeInsightFixtureTestCase() {
    fun testIncreaseLangLevel() {
        configureRuntime("mockRuntime11")
        resetProjectSettings(LanguageVersion.KOTLIN_1_0)
        myFixture.configureByText("foo.kt", "val x get() = 1")

        myFixture.launchAction(myFixture.findSingleIntention("Set project language version to 1.1"))

        assertEquals("1.1", KotlinCommonCompilerArgumentsHolder.getInstance(project).settings.languageVersion)
        assertEquals("1.0", KotlinCommonCompilerArgumentsHolder.getInstance(project).settings.apiVersion)
    }

    fun testIncreaseLangLevelFacet() {
        configureRuntime("mockRuntime11")
        resetProjectSettings(LanguageVersion.KOTLIN_1_0)
        configureKotlinFacet(myModule) {
            settings.languageLevel = LanguageVersion.KOTLIN_1_0
            settings.apiLevel = LanguageVersion.KOTLIN_1_0
        }
        myFixture.configureByText("foo.kt", "val x get() = 1")

        assertEquals(LanguageVersion.KOTLIN_1_0, myModule.languageVersionSettings.languageVersion)
        myFixture.launchAction(myFixture.findSingleIntention("Set module language version to 1.1"))
        assertEquals(LanguageVersion.KOTLIN_1_1, myModule.languageVersionSettings.languageVersion)
    }

    fun testIncreaseLangAndApiLevel() {
        configureRuntime("mockRuntime11")
        resetProjectSettings(LanguageVersion.KOTLIN_1_0)
        myFixture.configureByText("foo.kt", "val x = <caret>\"s\"::length")

        myFixture.launchAction(myFixture.findSingleIntention("Set project language version to 1.1"))

        assertEquals("1.1", KotlinCommonCompilerArgumentsHolder.getInstance(project).settings.languageVersion)
        assertEquals("1.1", KotlinCommonCompilerArgumentsHolder.getInstance(project).settings.apiVersion)
    }

    fun testIncreaseLangAndApiLevel_10() {
        configureRuntime("mockRuntime106")
        resetProjectSettings(LanguageVersion.KOTLIN_1_0)
        myFixture.configureByText("foo.kt", "val x = <caret>\"s\"::length")

        myFixture.launchAction(myFixture.findSingleIntention("Set project language version to 1.1"))

        assertEquals("1.1", KotlinCommonCompilerArgumentsHolder.getInstance(project).settings.languageVersion)
        assertEquals("1.1", KotlinCommonCompilerArgumentsHolder.getInstance(project).settings.apiVersion)

        assertEquals(bundledRuntimeVersion(), getRuntimeLibraryVersion(myFixture.module))
    }

    fun testIncreaseLangLevelFacet_10() {
        configureRuntime("mockRuntime106")
        resetProjectSettings(LanguageVersion.KOTLIN_1_0)
        configureKotlinFacet(myModule) {
            settings.languageLevel = LanguageVersion.KOTLIN_1_0
            settings.apiLevel = LanguageVersion.KOTLIN_1_0
        }
        myFixture.configureByText("foo.kt", "val x = <caret>\"s\"::length")

        assertEquals(LanguageVersion.KOTLIN_1_0, myModule.languageVersionSettings.languageVersion)
        myFixture.launchAction(myFixture.findSingleIntention("Set module language version to 1.1"))
        assertEquals(LanguageVersion.KOTLIN_1_1, myModule.languageVersionSettings.languageVersion)

        assertEquals(bundledRuntimeVersion(), getRuntimeLibraryVersion(myFixture.module))
    }

    fun testAddKotlinReflect() {
        configureRuntime("mockRuntime11")
        myFixture.configureByText("foo.kt", """class Foo(val prop: Any) {
                fun func() {}
            }

            fun y01() = Foo::prop.gett<caret>er
            """)
        myFixture.launchAction(myFixture.findSingleIntention("Add kotlin-reflect.jar to the classpath"))
        val kotlinRuntime = KotlinJavaModuleConfigurator.instance.getKotlinLibrary(myModule)!!
        val classes = kotlinRuntime.getFiles(OrderRootType.CLASSES).map { it.name }
        assertContainsElements(classes, "kotlin-reflect.jar")
        val sources = kotlinRuntime.getFiles(OrderRootType.SOURCES)
        assertContainsElements(sources.map { it.name }, "kotlin-reflect-sources.jar")
    }

    private fun configureRuntime(path: String) {
        val name = if (path == "mockRuntime106") "kotlin-runtime.jar" else "kotlin-stdlib.jar"
        val tempFile = File(FileUtil.createTempDirectory("kotlin-update-configuration", null), name)
        FileUtil.copy(File("idea/testData/configuration/$path/$name"), tempFile)
        val tempVFile = LocalFileSystem.getInstance().findFileByIoFile(tempFile)!!

        updateModel(myFixture.module) { model ->
            val editor = NewLibraryEditor()
            editor.name = "KotlinJavaRuntime"

            editor.addRoot(JarFileSystem.getInstance().getJarRootForLocalFile(tempVFile)!!, OrderRootType.CLASSES)

            ConfigLibraryUtil.addLibrary(editor, model)
        }
    }

    private fun resetProjectSettings(version: LanguageVersion) {
        KotlinCommonCompilerArgumentsHolder.getInstance(project).update {
            languageVersion = version.versionString
            apiVersion = version.versionString
        }
    }

    private val coroutineSupport: LanguageFeature.State
        get() = project.getLanguageVersionSettings().getFeatureSupport(LanguageFeature.Coroutines)

    override fun tearDown() {
        FacetManager.getInstance(myModule).getFacetByType(KotlinFacetType.TYPE_ID)?.let {
            FacetUtil.deleteFacet(it)
        }
        ConfigLibraryUtil.removeLibrary(myModule, "KotlinJavaRuntime")
        super.tearDown()
    }
}
