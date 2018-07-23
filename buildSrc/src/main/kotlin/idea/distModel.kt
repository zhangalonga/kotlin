package org.jetbrains.kotlin.buildUtils.idea

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.codehaus.groovy.runtime.GStringImpl
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.CompositeFileCollection
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.archive.ZipFileTree
import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.SingleParentCopySpec
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.util.concurrent.Callable
import kotlin.reflect.full.allSupertypes

class DistModelBuildContext(
        val parent: DistModelBuildContext?,
        val kind: String,
        val title: String
) {
    val depth: Int = parent?.depth ?: 0

    override fun toString() = "${"-".repeat(depth + 1)} $kind $title"
}

class DistModel(val rootProject: Project) {
    val byCopyTask = mutableSetOf<AbstractCopyTask>()
    val root = DistContainer(null, "<root>")
    val unresolved = mutableSetOf<DistElement>()

    fun ensureMapped(copy: AbstractCopyTask, depth: Int = 1) {
        if (byCopyTask.add(copy)) {
            val rootSpec = copy.rootSpec
            println("${"-".repeat(depth)} [$copy]")
            if (copy is Copy) {
                println("${"-".repeat(depth + 1)} INTO ${copy.destinationDir.path.replace(rootProject.projectDir.path, "$")}")
            }
            processCopySpec(rootSpec, depth + 1)
        } else {
            println("${"-".repeat(depth)} $copy ALREADY VISITED")
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
            processSourcePath(it, depth)
        }
    }

    fun processSourcePath(it: Any, depth: Int) {
        when {
            it is ShadowJar -> {
                println("${"-".repeat(depth)} FROM SHADOW JAR ${it.archivePath.path.replace(rootProject.projectDir.path, "$")}:")

                ensureMapped(it, depth + 1)
            }
            it is Jar -> {
                println("${"-".repeat(depth)} FROM JAR ${it.archivePath.path.replace(rootProject.projectDir.path, "$")}:")

                ensureMapped(it, depth + 1)
            }
            it is SourceSetOutput -> {
                println("${"-".repeat(depth)} FROM MODULE OUTPUT: ")

                it.classesDirs.files.forEach {
                    println("${"-".repeat(depth + 1)} " +
                            "${it.toString().replace(rootProject.projectDir.path, "$")}")
                }
            }
            it is Configuration -> {
                println("${"-".repeat(depth)} FROM CONFIGURATION: ")
                it.resolve().forEach {
                    println("${"-".repeat(depth + 1)} " +
                            "${it.toString().replace(rootProject.projectDir.path, "$")}")
                }
            }
            it is SourceDirectorySet -> {
                println("${"-".repeat(depth)} FROM SOURCES: ")
                it.srcDirs.forEach {
                    println("${"-".repeat(depth + 1)} " +
                            "${it.toString().replace(rootProject.projectDir.path, "$")}")
                }
            }
            it is CompositeFileCollection -> {
                println("${"-".repeat(depth)} FROM COMPOSITE FILE COLLECTION: ")
                it.visitRootElements(object : FileCollectionVisitor {
                    override fun visitDirectoryTree(directoryTree: DirectoryFileTree?) {
                        println("${"-".repeat(depth + 2)} UNSUPPORTED DIR TREE " +
                                "${directoryTree} (${directoryTree?.javaClass?.canonicalName})")
                    }

                    override fun visitTree(fileTree: FileTreeInternal?) {
                        println("${"-".repeat(depth + 2)} UNSUPPORTED TREE " +
                                "${fileTree} (${fileTree?.javaClass?.canonicalName})")
                    }

                    override fun visitCollection(fileCollection: FileCollectionInternal?) {
//                        println("${"-".repeat(depth + 2)} COLLECTION " +
//                                "${fileCollection} (${fileCollection?.javaClass?.canonicalName})")
                        processSourcePath(fileCollection!!, depth + 1)
                    }
                })
            }
            it is FileTreeAdapter && it.tree is ZipFileTree -> {
                val tree = it.tree
                val field = tree.javaClass.declaredFields.find { it.name == "zipFile" }!!
                field.isAccessible = true
                println("${"-".repeat(depth + 1)} " +
                        "${field.get(tree)}")
            }
            it is FileCollection -> {
                try {
                    it.files.forEach {
                        println("${"-".repeat(depth + 1)} " +
                                "${it.toString().replace(rootProject.projectDir.path, "$")}")
                    }
                } catch (t: Throwable) {
                    println("${"-".repeat(depth + 1)} ERROR: $t")
                }
            }
            it is String || it is GStringImpl -> {
                println("${"-".repeat(depth)} FROM $it")
            }
            it.toString() == "task ':prepare:build.version:writeBuildNumber'" -> Unit
            it is Callable<*> -> {
                processSourcePath(it.call(), depth)
            }
            it is Collection<*> -> {
                it.forEach {
                    processSourcePath(it!!, depth)
                }
            }
            else -> {
                val superclass = it.javaClass.superclass as Class<*>
                println("${"-".repeat(depth)} UNSUPPORTED TASK CLASS [${it.javaClass} " +
                        "implements: ${it.javaClass.interfaces.map { it.canonicalName }}] $it")
            }
        }
    }
}

sealed class DistElement(val parent: DistContainer?, val name: String) {
    init {
        parent?.addChild(this)
    }
}

open class DistContainer(parent: DistContainer?, name: String) : DistElement(parent, name) {
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

class DistDir(parent: DistContainer?, name: String) : DistContainer(parent, name) {

}

class DistJar(parent: DistContainer?, name: String): DistContainer(parent, name) {

}

class DistModuleOutput(val sourceSets: List<SourceSet>)

class SourceSet(val dir: File)