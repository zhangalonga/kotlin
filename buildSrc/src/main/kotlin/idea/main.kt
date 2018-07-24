package org.jetbrains.kotlin.buildUtils.idea

import IntelliJInstrumentCodeTask
import idea.DistModelBuildContext
import org.gradle.api.Project
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.gradle.ext.IdeArtifacts
import java.io.File

fun generateIdeArtifacts(rootProject: Project, ideArtifacts: IdeArtifacts) {
    val model = DistModelBuilder(rootProject)

    val reportsDir = File("${rootProject.buildDir}/reports/idea-artifacts-cfg")
    val projectDir = rootProject.projectDir

    File(reportsDir, "01-visitor.report.txt").printWriter().use { pw ->
        val ctx = DistModelBuildContext(null, "ROOT", "dist", pw)

        fun visitAllTasks(project: Project) {
            project.tasks.forEach {
                try {
                    when {
                        it is AbstractCopyTask -> model.visitCopyTask(it, ctx)
                        it is AbstractCompile -> model.visitCompileTask(it, ctx)
                        it is IntelliJInstrumentCodeTask -> model.visitInterumentTask(it, ctx)
                        it.name == "stripMetadata" -> {
                            ctx.log("STRIP METADATA", "${it.inputs.files.singleFile} -> ${it.outputs.files.singleFile}")
                            DistCopy(
                                    model.requirePath(it.outputs.files.singleFile.path),
                                    model.requirePath(it.inputs.files.singleFile.path)
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
                target = model.requirePath("$projectDir/libraries/reflect/build/libs/kotlin-reflect-proguard.jar"),
                src = model.requirePath("$projectDir/libraries/reflect/build/libs/kotlin-reflect-shadow.jar")
        )

        // todo: investigate
        val version = rootProject.version
        DistCopy(
                target = model.requirePath("$projectDir/dist/kotlinc/lib"),
                src = model.requirePath("$projectDir/libraries/stdlib/runtime/build/libs/kotlin-runtime-$version.jar")
        )
        DistCopy(
                target = model.requirePath("$projectDir/dist/kotlinc/lib"),
                src = model.requirePath("$projectDir/libraries/stdlib/runtime/build/libs/kotlin-runtime-$version-sources.jar")
        )
        DistCopy(
                target = model.requirePath("$projectDir/dist/kotlinc/lib"),
                customTargetName = "kotlin-stdlib.jar",
                src = model.requirePath("$projectDir/libraries/stdlib/jvm/build/libs/dist-kotlin-stdlib.jar")
        )
        DistCopy(
                target = model.requirePath("$projectDir/dist/kotlinc/lib"),
                customTargetName = "kotlin-stdlib-sources.jar",
                src = model.requirePath("$projectDir/libraries/stdlib/jvm/build/libs/dist-kotlin-stdlib-sources.jar")
        )
    }

    File(reportsDir, "02-vfs.txt").printWriter().use {
        model.vfsRoot.printTree(it)
    }
    model.checkRefs()

    with(DistModelFlattener(rootProject)) {
        with(DistModelIdeaArtifactBuilder(rootProject)) {
            File(reportsDir, "03-flattened-vfs.txt").printWriter().use { report ->
                fun getFlattenned(vfsPath: String): DistVFile =
                        model.vfsRoot.relativePath("$projectDir/$vfsPath")
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