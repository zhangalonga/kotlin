package idea

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
import org.jetbrains.kotlin.buildUtils.idea.DistContainer
import org.jetbrains.kotlin.buildUtils.idea.DistElement
import java.io.File
import java.util.concurrent.Callable

class DistModelBuildContext(
        val parent: DistModelBuildContext?,
        val kind: String,
        val title: String,
        val report: Appendable? = parent?.report
) {
    val depth: Int = if (parent != null) parent.depth + 1 else 0

    init {
        report?.appendln(toString())
    }

    val logPrefix get() = "-".repeat(depth + 1)

    fun log(kind: String, title: String = "") {
        report?.appendln("$logPrefix- $kind $title")
    }

    fun logUnsupported(kind: String, obj: Any?) {
        val classInfo = if (obj != null) {
            val javaClass = obj.javaClass
            val superclass = javaClass.superclass as Class<*>
            " [$javaClass extends $superclass implements ${javaClass.interfaces.map { it.canonicalName }}]"
        } else ""

        log("UNSUPPORTED $kind", "$obj $classInfo")
    }

    override fun toString() = "$logPrefix $kind $title"
}

fun DistModelBuildContext?.child(kind: String, title: String = "") =
        DistModelBuildContext(this, kind, title)

class DistModel(val rootProject: Project) {
    val byCopyTask = mutableSetOf<AbstractCopyTask>()
    val root = DistContainer(null, "<root>")
    val unresolved = mutableSetOf<DistElement>()

    fun getRelativePath(path: String) = path.replace(rootProject.projectDir.path, "$")

    fun ensureMapped(copy: AbstractCopyTask, parentContext: DistModelBuildContext?) {
        val context = parentContext.child("FROM COPY TASK", copy.path)

        if (byCopyTask.add(copy)) {
            val rootSpec = copy.rootSpec
            if (copy is Copy) {
                context.log("INTO", getRelativePath(copy.destinationDir.path))
            }

            processCopySpec(rootSpec, context)
        } else {
            context.log("ALREADY VISITED")
        }
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

    fun processSourcePath(it: Any, parentContext: DistModelBuildContext) {
        when {
            it is ShadowJar -> ensureMapped(it, parentContext.child("FROM SHADOW JAR", getRelativePath(it.archivePath.path)))
            it is Jar -> ensureMapped(it, parentContext.child("FROM JAR", getRelativePath(it.archivePath.path)))
            it is SourceSetOutput -> {
                val ctx = parentContext.child("FROM MODULE OUTPUT")

                it.classesDirs.files.forEach {
                    ctx.log("", getRelativePath(it.toString()))
                }
            }
            it is Configuration -> {
                val ctx = parentContext.child("FROM CONFIGURATION")

                it.resolve().forEach {
                    ctx.log("", getRelativePath(it.toString()))
                }
            }
            it is SourceDirectorySet -> {
                val ctx = parentContext.child("FROM SOURCES")

                it.srcDirs.forEach {
                    ctx.log("", getRelativePath(it.toString()))
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
                        processSourcePath(fileCollection!!, parentContext)
                    }
                })
            }
            it is FileTreeAdapter && it.tree is ZipFileTree -> {
                val tree = it.tree
                val field = tree.javaClass.declaredFields.find { it.name == "zipFile" }!!
                field.isAccessible = true
                val zipFile = field.get(tree) as File

                parentContext.log("", getRelativePath(zipFile.path))
            }
            it is FileCollection -> {
                it.files.forEach {
                    parentContext.log("", getRelativePath(it.path))
                }
            }
            it is String || it is GStringImpl -> {
                parentContext.log("", getRelativePath(it.toString()))
            }
            it.toString() == "task ':prepare:build.version:writeBuildNumber'" -> Unit
            it is Callable<*> -> {
                processSourcePath(it.call(), parentContext)
            }
            it is Collection<*> -> {
                it.forEach {
                    processSourcePath(it!!, parentContext)
                }
            }
            else -> parentContext.logUnsupported("SOURCE PATH", it)
        }
    }
}