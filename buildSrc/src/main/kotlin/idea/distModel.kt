package org.jetbrains.kotlin.buildUtils.idea

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.SingleParentCopySpec
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.jvm.tasks.Jar
import java.io.File

class DistModel(val rootProject: Project) {
    val byCopyTask = mutableSetOf<AbstractCopyTask>()
    val root = DistContainer(null, "<root>")

    fun ensureMapped(copy: AbstractCopyTask, depth: Int = 1) {
        if (byCopyTask.add(copy)) {
            println("${"-".repeat(depth)} $copy")

//            val destDir = root.getOrCreateDirByPath(copy.rootSpec.destinationDir.path)

            processCopySpec(copy.rootSpec, depth)
        }
    }

    fun processCopySpec(spec: CopySpecInternal, depth: Int) {
        spec.children.forEach {
            if (it is SingleParentCopySpec) {
                processSingleParentCopySpec(it, depth)
            } else if (it is CopySpecInternal) {
                processCopySpec(it, depth)
            } else error("Unsupported copySpec: $spec")
        }
    }

    fun processSingleParentCopySpec(spec: SingleParentCopySpec, depth: Int) {
        val sourcePaths = spec.sourcePaths
        sourcePaths.forEach {
            when {
                it is Jar -> {
                    println("${"-".repeat(depth)} JAR:")

                    ensureMapped(it, depth + 1)
                }
                it is SourceSetOutput -> {
                    println("${"-".repeat(depth + 1)} MODULE OUTPUT: ")

                    it.classesDirs.files.forEach {
                        println("${"-".repeat(depth + 2)} " +
                                "${it.toString().removePrefix(rootProject.projectDir.path)}")
                    }
                }
                it is Configuration -> {
                    println("${"-".repeat(depth + 1)} CONFIGURATION: ")
                    it.resolve().forEach {
                        println("${"-".repeat(depth + 2)} " +
                                "${it.toString().removePrefix(rootProject.projectDir.path)}")
                    }
                }
                it is SourceDirectorySet -> {
                    println("${"-".repeat(depth + 1)} SOURCES: ")
                    it.srcDirs.forEach {
                        println("${"-".repeat(depth + 2)} " +
                                "${it.toString().removePrefix(rootProject.projectDir.path)}")
                    }
                }
                it.toString() == "task ':prepare:build.version:writeBuildNumber'" -> Unit
                else -> println("${"-".repeat(depth)} UNSUPPORTED TASK CLASS [${it.javaClass}] $it")
            }
        }
    }
}

sealed class DistElement(val parent: DistContainer?, val name: String) {
    init {
        parent?.addChild(this)
    }
}

class DistContainer(parent: DistContainer?, name: String) : DistElement(parent, name) {
    val childByName = mutableMapOf<String, DistElement>()

    fun getOrCreateDirByPath(path: String) =
            path.split(File.pathSeparator).fold(this) { parent, childName ->
                parent.getOrCreateDir(childName)
            }

    fun getOrCreateDir(name: String): DistContainer {
        val child = childByName[name]

        return if (child != null) {
            child as? DistContainer ?: error("directory expected, but `$child` found")
        } else {
            DistContainer(this, name)
        }
    }

    fun addChild(child: DistElement) {
        val prev = childByName.put(child.name, child)
        check(prev == null) { "Cannot add `$child`, element with this name already existed: `$prev`" }
    }
}
