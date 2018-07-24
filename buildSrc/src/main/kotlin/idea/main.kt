package org.jetbrains.kotlin.buildUtils.idea

import IntelliJInstrumentCodeTask
import org.gradle.api.Project
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.gradle.ext.IdeArtifacts
import java.io.File

fun generateIdeArtifacts(rootProject: Project, ideArtifacts: IdeArtifacts) {
    val reportsDir = File("${rootProject.buildDir}/reports/idea-artifacts-cfg")
    val projectDir = rootProject.projectDir

    File(reportsDir, "01-visitor.report.txt").printWriter().use { visitorReport ->
        val modelBuilder = DistModelBuilder(rootProject, visitorReport)

        fun visitAllTasks(project: Project) {
            project.tasks.forEach {
                try {
                    when {
                        it is AbstractCopyTask -> modelBuilder.visitCopyTask(it)
                        it is AbstractCompile -> modelBuilder.visitCompileTask(it)
                        it is IntelliJInstrumentCodeTask -> modelBuilder.visitInterumentTask(it)
                        it.name == "stripMetadata" -> {
                            modelBuilder.rootCtx.log(
                                    "STRIP METADATA",
                                    "${it.inputs.files.singleFile} -> ${it.outputs.files.singleFile}"
                            )

                            DistCopy(
                                    modelBuilder.requirePath(it.outputs.files.singleFile.path),
                                    modelBuilder.requirePath(it.inputs.files.singleFile.path)
                            )
                        }
                    }
                } catch (t: Throwable) {
                    println("Error while visiting `$it`")
                    t.printStackTrace()
                }
            }

            project.subprojects.forEach {
                visitAllTasks(it)
            }
        }

        visitAllTasks(rootProject)

        // proguard
        DistCopy(
                target = modelBuilder.requirePath("$projectDir/libraries/reflect/build/libs/kotlin-reflect-proguard.jar"),
                src = modelBuilder.requirePath("$projectDir/libraries/reflect/build/libs/kotlin-reflect-shadow.jar")
        )

        // todo: investigate
        val version = rootProject.version
        DistCopy(
                target = modelBuilder.requirePath("$projectDir/dist/kotlinc/lib"),
                src = modelBuilder.requirePath("$projectDir/libraries/stdlib/runtime/build/libs/kotlin-runtime-$version.jar")
        )
        DistCopy(
                target = modelBuilder.requirePath("$projectDir/dist/kotlinc/lib"),
                src = modelBuilder.requirePath("$projectDir/libraries/stdlib/runtime/build/libs/kotlin-runtime-$version-sources.jar")
        )
        DistCopy(
                target = modelBuilder.requirePath("$projectDir/dist/kotlinc/lib"),
                customTargetName = "kotlin-stdlib.jar",
                src = modelBuilder.requirePath("$projectDir/libraries/stdlib/jvm/build/libs/dist-kotlin-stdlib.jar")
        )
        DistCopy(
                target = modelBuilder.requirePath("$projectDir/dist/kotlinc/lib"),
                customTargetName = "kotlin-stdlib-sources.jar",
                src = modelBuilder.requirePath("$projectDir/libraries/stdlib/jvm/build/libs/dist-kotlin-stdlib-sources.jar")
        )


        File(reportsDir, "02-vfs.txt").printWriter().use {
            modelBuilder.vfsRoot.printTree(it)
        }
        modelBuilder.checkRefs()

        with(DistModelFlattener(rootProject)) {
            with(DistModelIdeaArtifactBuilder(rootProject)) {
                File(reportsDir, "03-flattened-vfs.txt").printWriter().use { report ->
                    fun getFlattenned(vfsPath: String): DistVFile =
                            modelBuilder.vfsRoot.relativePath("$projectDir/$vfsPath")
                                    .flatten()
                                    .also { it.printTree(report) }

                    ideArtifacts.ideArtifact("ideaPlugin") {
                        directory("kotlinc") {
                            addFiles(getFlattenned("dist/kotlinc"))
                        }
                        directory("lib") {
                            addFiles(getFlattenned("dist/artifacts/ideaPlugin/Kotlin/lib"))
                        }
                    }
                }
            }
        }
    }
}