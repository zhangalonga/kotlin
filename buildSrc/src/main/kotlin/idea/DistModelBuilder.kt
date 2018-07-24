package org.jetbrains.kotlin.buildUtils.idea

import IntelliJInstrumentCodeTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import idea.DistModelBuildContext
import idea.child
import org.codehaus.groovy.runtime.GStringImpl
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
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
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.util.concurrent.Callable

class DistModelBuilder(val rootProject: Project) {
    val visited = mutableSetOf<Task>()
    val vfsRoot = DistVFile(null, "<root>", File(""))
    val refs = mutableSetOf<DistVFile>()

    fun visitInterumentTask(it: IntelliJInstrumentCodeTask, parentCtx: DistModelBuildContext) {
        if (visited.add(it)) {
            // todo:
            val ctx = parentCtx.child("INSTRUMENT", it.path)
            ctx.setDest(it.output!!.path)
            processSourcePath(it.originalClassesDirs, ctx)
            val dest = ctx.destination
//            val sourceSet = it.sourceSet
            if (dest != null) {
                DistModuleOutput(dest, it.project.path)
            }
        }
    }

    fun visitCompileTask(it: AbstractCompile, parentContext: DistModelBuildContext) {
        if (visited.add(it)) {
            val ctx = parentContext.child("COMPILE", it.path)
            ctx.setDest(it.destinationDir.path)
            val dest = ctx.destination
            if (dest != null) DistModuleOutput(dest, it.project.path)
            else ctx.logUnsupported("Cannot add contents: destination is unknown", it)
        }
    }

    fun visitCopyTask(
            copy: AbstractCopyTask,
            parentContext: DistModelBuildContext?,
            shade: Boolean = false
    ): DistModelBuildContext {
        val context = parentContext.child("FROM COPY TASK", copy.path, shade)

        if (visited.add(copy)) {
            val rootSpec = copy.rootSpec

            when (copy) {
                is Copy -> context.setDest(copy.destinationDir.path)
                is Sync -> context.setDest(copy.destinationDir.path)
                is AbstractArchiveTask -> context.setDest(copy.archivePath.path)
            }

            when (copy) {
                is ShadowJar -> copy.configurations.forEach {
                    processSourcePath(it, context)
                }
            }

            processCopySpec(rootSpec, context)
        } else {
            context.log("ALREADY VISITED")
        }

        return context
    }

    fun processCopySpec(spec: CopySpecInternal, parentContext: DistModelBuildContext) {
        spec.children.forEach {
            when (it) {
                is SingleParentCopySpec -> processSingleParentCopySpec(it, parentContext)
                is CopySpecInternal -> processCopySpec(it, parentContext)
                else -> parentContext.logUnsupported("CopySpec", spec)
            }
        }
    }

    fun processSingleParentCopySpec(spec: SingleParentCopySpec, parentContext: DistModelBuildContext) {
        val sourcePaths = spec.sourcePaths
        sourcePaths.forEach {
            processSourcePath(it, parentContext)
        }
    }

    fun processSourcePath(it: Any?, parentContext: DistModelBuildContext) {
        when {
            it == null -> Unit
            it is ShadowJar -> parentContext.addCopyOf(it.archivePath.path) { src, target ->
                visitCopyTask(
                        it,
                        parentContext.child("FROM SHADOW JAR", getRelativePath(it.archivePath.path)),
                        true
                )
            }
            it is Jar -> parentContext.addCopyOf(it.archivePath.path) { src, target ->
                visitCopyTask(
                        it,
                        parentContext.child("FROM JAR", getRelativePath(it.archivePath.path))
                )
            }
            it is SourceSetOutput -> {
                val ctx = parentContext.child("FROM MODULE OUTPUT")

                it.classesDirs.files.forEach {
                    ctx.addCopyOf(it.path)
                }
            }
            it is Configuration -> {
                val ctx = parentContext.child("FROM CONFIGURATION")

                it.resolve().forEach {
                    ctx.addCopyOf(it.path)
                }
            }
            it is SourceDirectorySet -> {
                val ctx = parentContext.child("FROM SOURCES")

                it.srcDirs.forEach {
                    ctx.addCopyOf(it.path)
                }
            }
            it is CompositeFileCollection -> {
                it.visitRootElements(object : FileCollectionVisitor {
                    override fun visitDirectoryTree(directoryTree: DirectoryFileTree?) {
                        parentContext.logUnsupported("DIR TREE", directoryTree)
                    }

                    override fun visitTree(fileTree: FileTreeInternal?) {
                        parentContext.logUnsupported("TREE", fileTree)
                    }

                    override fun visitCollection(fileCollection: FileCollectionInternal?) {
                        processSourcePath(fileCollection, parentContext)
                    }
                })
            }
            it is FileTreeAdapter && it.tree is ZipFileTree -> {
                val tree = it.tree
                val field = tree.javaClass.declaredFields.find { it.name == "zipFile" }!!
                field.isAccessible = true
                val zipFile = field.get(tree) as File

                parentContext.addCopyOf(zipFile.path) { src, target ->
                    DistCopy(target, src)
                }
            }
            it is FileTreeInternal -> {
                // todo: preserve or warn about filtering
                it.visitTreeOrBackingFile(object : FileVisitor {
                    override fun visitFile(fileDetails: FileVisitDetails) {
                        parentContext.addCopyOf(fileDetails.file.path)
                    }

                    override fun visitDir(dirDetails: FileVisitDetails) {
                        parentContext.addCopyOf(dirDetails.file.path)
                    }
                })
            }
            it is FileCollection -> {
                try {
                    it.files.forEach {
                        parentContext.addCopyOf(it.path)
                    }
                } catch (t: Throwable) {
                    parentContext.logUnsupported("FILE COLLECTION (${t.message})", it)
                }
            }
            it is String || it is GStringImpl -> parentContext.addCopyOf(it.toString())
//            it.toString() == "task ':prepare:build.version:writeBuildNumber'" -> {
//                // todo:
//            }
            it is Callable<*> -> {
                processSourcePath(it.call(), parentContext)
            }
            it is Collection<*> -> {

                it.forEach {
                    processSourcePath(it, parentContext)
                }
            }
            it is Copy -> {
                val src = visitCopyTask(it, parentContext).destination
                if (src != null) parentContext.addCopyOf(src)
                // else it is added to `it`, because destination is inhereted by context
            }
            it is File -> {
                parentContext.addCopyOf(it.path)
            }
            else -> parentContext.logUnsupported("SOURCE PATH", it)
        }
    }

    inline fun DistModelBuildContext.addCopyOf(
            src: String,
            body: (src: DistVFile, target: DistVFile) -> Unit = { _, _ -> Unit }
    ) {
        addCopyOf(requirePath(src), body)
    }

    inline fun DistModelBuildContext.addCopyOf(
            src: DistVFile,
            body: (src: DistVFile, target: DistVFile) -> Unit = { _, _ -> Unit }
    ) {
        val destination = destination
        if (destination != null) {
            body(src, destination)
            DistCopy(destination, src)
            log("+", src.file.path)
        } else logUnsupported("Cannot add copy of `$src`: destination is unknown")
    }

    fun DistModelBuildContext.setDest(path: String) {
        destination = vfsRoot.relativePath(path)
        log("INTO", getRelativePath(path))
    }

    fun checkRefs() {
        refs.forEach {
            if (!it.hasContents && it.contents.isEmpty() && it.file.path.contains("/build/")) {
                println("UNRESOLVED ${it.file}")
                it.contents.forEach {
                    println("+ ${it}")
                }
            }
        }
    }

    fun getRelativePath(path: String) = path.replace(rootProject.projectDir.path, "$")

    fun requirePath(targetPath: String): DistVFile {
        val target = vfsRoot.relativePath(targetPath)
        if (!File(targetPath).exists()) refs.add(target)
        return target
    }
}