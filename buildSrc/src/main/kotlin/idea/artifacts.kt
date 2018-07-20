package org.jetbrains.kotlin.buildUtils.idea

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import ideaPlugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.file.copy.SingleParentCopySpec
import org.gradle.api.tasks.Copy
import org.gradle.jvm.tasks.Jar
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

    println("----------------- vv DIST vv ------------------------")
    distTasks.forEach {
        model.ensureMapped(it)
    }
    println("---------------- ^^ DIST ^^ -------------------------")


    val mainIdeaPluginTask = rootProject.tasks.getByName("ideaPlugin")

    val ideaPluginTasks = mainIdeaPluginTask.taskDependencies
            .getDependencies(mainIdeaPluginTask)
            .filter { it.name == "ideaPlugin" }
            .filterIsInstance<Copy>()

    println("----------------- vv IDEA PLUGIN vv ------------------------")
    ideaPluginTasks.forEach {
        model.ensureMapped(it)
    }
    println("---------------- ^^ IDEA PLUGIN ^^ -------------------------")
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