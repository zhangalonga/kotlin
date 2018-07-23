package org.jetbrains.kotlin.buildUtils.idea

import idea.DistModelBuildContext
import org.gradle.api.Project
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.gradle.ext.IdeArtifacts
import java.io.File

fun generateIdeArtifacts(rootProject: Project, ideArtifacts: IdeArtifacts) {
    val model = DistModelBuilder(rootProject)

    val reportsDir = File("${rootProject.buildDir}/reports/idea-artifacts-cfg")

    File(reportsDir, "01-visitor.report.txt").printWriter().use { pw ->
        val ctx = DistModelBuildContext(null, "ROOT", "dist", pw)

        fun visitAllTasks(project: Project) {
            project.tasks.forEach {
                try {
                    when (it) {
                        is AbstractCopyTask -> model.visitCopyTask(it, ctx)
                        is AbstractCompile -> model.visitCompileTask(it, ctx)
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
    }

    File(reportsDir, "02-vfs.txt").printWriter().use {
        model.vfsRoot.printTree(it)
    }
    model.checkRefs()

    with(DistModelFlattener(rootProject)) {
        with(DistModelIdeaArtifactBuilder(rootProject)) {
            fun configureArtifact(
                    artifactName: String,
                    fromVfsPath: String
            ) {
                File(reportsDir, "03-$artifactName.flattened-vfs.txt").printWriter().use { report ->
                    val flatenned = model.vfsRoot.relativePath("${rootProject.projectDir}/$fromVfsPath").flatten()
                    flatenned.printTree(report)

                    ideArtifacts.ideArtifact(artifactName) {
                        addFiles(flatenned)
                    }
                }
            }

            configureArtifact(artifactName = "kotlinc", fromVfsPath = "dist/kotlinc")
            configureArtifact(artifactName = "ideaPlugin", fromVfsPath = "dist/artifacts/ideaPlugin/Kotlin/lib")
        }
    }
}