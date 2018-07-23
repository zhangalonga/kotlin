package org.jetbrains.kotlin.buildUtils.idea

import idea.DistModelBuildContext
import idea.DistModelBuilder
import idea.DistModelFlattener
import org.gradle.api.Project
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.gradle.ext.ArtifactType
import org.jetbrains.gradle.ext.IdeArtifacts
import org.jetbrains.gradle.ext.RecursiveArtifact
import java.io.File

fun generateIdeArtifacts(rootProject: Project, ideArtifacts: IdeArtifacts) {
    val model = DistModelBuilder(rootProject)

    val reportsDir = File("/Users/jetbrains/tasks/jps-build-test/kotlin/buildSrc/src/main/kotlin/idea")

    File(reportsDir, "all.report.txt").printWriter().use { pw ->
        val ctx = DistModelBuildContext(null, "ROOT", "dist", pw)

        fun visitAllTasks(project: Project) {
            project.tasks.forEach {
                try {
                    if (it is AbstractCopyTask) {
                        model.visitCopyTask(it, ctx)
                    } else if (it is AbstractCompile) {
                        model.visitCompileTask(it, ctx)
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

    fun visitDistRootCopyTask(name: String) {
        val mainDistTask = rootProject.tasks.getByName(name)
        val distTasks = mainDistTask.taskDependencies
                .getDependencies(mainDistTask)
                .filter { it.name == name }
                .filterIsInstance<Copy>()

        File(reportsDir, "$name.report.txt").printWriter().use { pw ->
            val ctx = DistModelBuildContext(null, "ROOT", name, pw)
            distTasks.forEach {
                model.visitCopyTask(it, ctx)
            }
        }
    }

    File(reportsDir, "vfs.txt").printWriter().use {
        model.vfsRoot.printTree(it)
    }
    model.checkRefs()

    val flattener = DistModelFlattener(rootProject)

    File(reportsDir, "kotlinc.vfs.txt").printWriter().use {
        with(flattener) {
            val flatenned = model.vfsRoot.relativePath("${rootProject.projectDir}/dist/kotlinc").flatten()
            flatenned.printTree(it)

            ideArtifacts.ideArtifact("kotlinc") {
                generateIdeArtifact(rootProject, this, flatenned, false)
            }
        }
    }

    File(reportsDir, "idea-plugin.vfs.txt").printWriter().use {
        with(flattener) {
            val flatenned = model.vfsRoot.relativePath("${rootProject.projectDir}/dist/artifacts/ideaPlugin/Kotlin/lib").flatten()
            flatenned.printTree(it)

            ideArtifacts.ideArtifact("ideaPlugin") {
                generateIdeArtifact(rootProject, this, flatenned, false)
            }
        }
    }
}

fun generateIdeArtifact(rootProject: Project, recursiveArtifact: RecursiveArtifact, flatenned: BuildVFile, inJar: Boolean) {
    val children = mutableSetOf<String>()

    flatenned.contents.forEach {
        when (it) {
            is DistCopy -> {
                val file = it.src.file
                if (inJar && file.name.endsWith(".jar")) {
                    recursiveArtifact.extractedDirectory(file.path)
                } else if (file.isDirectory) {
                    children.add(file.name)
                    recursiveArtifact.directoryContent(file.path)
                } else {
                    children.add(file.name)
                    recursiveArtifact.file(file.path)
                }
            }
            is DistModuleOutput -> {
                val findProject = rootProject.findProject(it.projectId)
                val idea = findProject?.extensions?.findByName("idea") as? org.gradle.plugins.ide.idea.model.IdeaModel
                val name = idea?.module?.name
                if (name != null) {
                    recursiveArtifact.moduleOutput(name + "_main")
                } else {
                    println(it.projectId)
                    recursiveArtifact.moduleOutput(it.projectId) // for debug only
                }
            }
        }
    }

    flatenned.child.values.forEach {
        if (it.name !in children) {
            if (it.name.endsWith(".jar")) {
                generateIdeArtifact(rootProject, recursiveArtifact.jar(it.name), it, true)
            } else {
                generateIdeArtifact(rootProject, recursiveArtifact.directory(it.name), it, inJar)
            }
        }
    }
}

operator fun RecursiveArtifact.get(name: String): RecursiveArtifact? =
        this.children.filterIsInstance<RecursiveArtifact>().find { it.name == name }

fun RecursiveArtifact.getOrCreateDirectoryAtPath(relativePath: String) =
        relativePath.split(File.pathSeparator).foldRight(this) { childName, parentArtifact ->
            parentArtifact.get(childName) ?: directory(childName)
        }

fun RecursiveArtifact.add(name: String, artifactType: ArtifactType): RecursiveArtifact {
    val result = this.project.objects.newInstance(RecursiveArtifact::class.java, this.project, name, artifactType)
            as RecursiveArtifact
    this.children.add(result)
    return result
}

fun RecursiveArtifact.directory(name: String): RecursiveArtifact = add(name, ArtifactType.DIR)
fun RecursiveArtifact.jar(name: String): RecursiveArtifact = add(name, ArtifactType.ARCHIVE)