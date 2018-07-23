package org.jetbrains.kotlin.buildUtils.idea

import idea.DistModel
import idea.DistModelBuildContext
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.jetbrains.gradle.ext.ArtifactType
import org.jetbrains.gradle.ext.RecursiveArtifact
import java.io.File

fun generateIdeArtifacts(rootProject: Project) {
    val model = DistModel(rootProject)

    val mainDistTask = rootProject.tasks.getByName("dist")
    val distTasks = mainDistTask.taskDependencies
            .getDependencies(mainDistTask)
            .filter { it.name == "dist" }
            .filterIsInstance<Copy>()

    val reportsDir = File("/Users/jetbrains/tasks/jps-build-test/kotlin/buildSrc/src/main/kotlin/idea")

    File(reportsDir, "dist.report.txt").printWriter().use { pw ->
        val ctx = DistModelBuildContext(null, "ROOT", "dist", pw)
        distTasks.forEach {
            model.ensureMapped(it, ctx)
        }
    }

    val mainIdeaPluginTask = rootProject.tasks.getByName("ideaPlugin")

    val ideaPluginTasks = mainIdeaPluginTask.taskDependencies
            .getDependencies(mainIdeaPluginTask)
            .filter { it.name == "ideaPlugin" }
            .filterIsInstance<Copy>()

    File(reportsDir, "idea-plugin.report.txt").printWriter().use { pw ->
        val ctx = DistModelBuildContext(null, "ROOT", "idea-plugin", pw)
        ideaPluginTasks.forEach {
            model.ensureMapped(it, ctx)
        }
    }
}

operator fun RecursiveArtifact.get(name: String): RecursiveArtifact? =
        this.children.filterIsInstance<RecursiveArtifact>().find { it.name == name }

fun RecursiveArtifact.getOrCreateDirectoryAtPath(relativePath: String) =
        relativePath.split(File.pathSeparator).foldRight(this) { childName, parentArtifact ->
            parentArtifact.get(childName) ?: directory(childName)
        }

fun RecursiveArtifact.directory(name: String): RecursiveArtifact {
    val result = this.project.objects.newInstance(RecursiveArtifact::class.java, this.project, name, ArtifactType.DIR)
            as RecursiveArtifact
    this.children.add(result)
    return result
}