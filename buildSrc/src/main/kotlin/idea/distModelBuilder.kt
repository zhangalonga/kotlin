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
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.internal.file.copy.SingleParentCopySpec
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.buildUtils.idea.BuildVFile
import org.jetbrains.kotlin.buildUtils.idea.DistCopy
import org.jetbrains.kotlin.buildUtils.idea.DistModuleOutput
import java.io.File
import java.util.concurrent.Callable

class DistModelBuildContext(
        val parent: DistModelBuildContext?,
        val kind: String,
        val title: String,
        val report: Appendable? = parent?.report,
        val shade: Boolean = parent?.shade ?: false
) {
    var distContainer: BuildVFile? = parent?.distContainer
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

fun DistModelBuildContext?.child(kind: String, title: String = "", shade: Boolean = false) =
        DistModelBuildContext(this, kind, title, shade = shade)

class DistModelBuilder(val rootProject: Project) {
    val byCopyTask = mutableSetOf<AbstractCopyTask>()
    val vfsRoot = BuildVFile(null, "<root>", File(""))
    val refs = mutableSetOf<BuildVFile>()

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

    fun requirePath(targetPath: String): BuildVFile {
        val target = vfsRoot.relativePath(targetPath)
        if (!File(targetPath).exists()) {
            refs.add(target)
        }
        return target
    }

    fun DistModelBuildContext.setDest(path: String) {
        distContainer = vfsRoot.relativePath(path)
        log("INTO", getRelativePath(path))
    }

    fun visitCompileTask(it: AbstractCompile, parentContext: DistModelBuildContext) {
        val ctx = parentContext.child("COMPILE", it.path)
        ctx.setDest(it.destinationDir.path)
        val dest = ctx.distContainer
        if (dest != null) {
            DistModuleOutput(dest, it.project.path)
        } else {
            ctx.log("!", "Cann add contents: distContainer is unknown")
        }
    }

    inline fun DistModelBuildContext.addDistContents(
            src: String,
            body: (src: BuildVFile, target: BuildVFile) -> Unit = { _, _ -> Unit }
    ) {
        addDistContents(requirePath(src), body)
    }

    inline fun DistModelBuildContext.addDistContents(
            src: BuildVFile,
            body: (src: BuildVFile, target: BuildVFile) -> Unit = { _, _ -> Unit }
    ) {
        val distContainer1 = distContainer
        if (distContainer1 != null) {
            val target = distContainer1
            body(src, target)
            DistCopy(target, src)
            log("+", src.file.path)
        } else {
            log("!", "Cann add contents: distContainer is unknown")
        }
    }

    fun visitCopyTask(
            copy: AbstractCopyTask,
            parentContext: DistModelBuildContext?,
            shade: Boolean = false
    ): DistModelBuildContext {
        val context = parentContext.child("FROM COPY TASK", copy.path, shade)

        if (byCopyTask.add(copy)) {
            val rootSpec = copy.rootSpec

            when (copy) {
                is Copy -> {
                    context.setDest(copy.destinationDir.path)
                }
                is AbstractArchiveTask -> context.setDest(copy.archivePath.path)
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
            it is ShadowJar -> parentContext.addDistContents(it.archivePath.path) { src, target ->
                visitCopyTask(
                        it,
                        parentContext.child("FROM SHADOW JAR", getRelativePath(it.archivePath.path)),
                        true
                )
            }
            it is Jar -> parentContext.addDistContents(it.archivePath.path) { src, target ->
                visitCopyTask(
                        it,
                        parentContext.child("FROM JAR", getRelativePath(it.archivePath.path))
                )
            }
            it is SourceSetOutput -> {
                val ctx = parentContext.child("FROM MODULE OUTPUT")

                it.classesDirs.files.forEach {
                    ctx.addDistContents(it.path)
                }
            }
            it is Configuration -> {
                val ctx = parentContext.child("FROM CONFIGURATION")

                it.resolve().forEach {
                    ctx.addDistContents(it.path)
                }
            }
            it is SourceDirectorySet -> {
                val ctx = parentContext.child("FROM SOURCES")

                it.srcDirs.forEach {
                    ctx.addDistContents(it.path)
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

                parentContext.addDistContents(zipFile.path) { src, target ->
                    DistCopy(target, src)
                }
            }
            it is FileCollection -> {
                parentContext.logUnsupported("FILE COLLECTION", it)

                it.files.forEach {
                    parentContext.addDistContents(it.path)
                }
            }
            it is String || it is GStringImpl -> parentContext.addDistContents(it.toString())
            it.toString() == "task ':prepare:build.version:writeBuildNumber'" -> {
                // todo:
            }
            it is Callable<*> -> {
                processSourcePath(it.call(), parentContext)
            }
            it is Collection<*> -> {
                it.forEach {
                    processSourcePath(it, parentContext)
                }
            }
            it is Copy -> {
                val src = visitCopyTask(it, parentContext).distContainer
                if (src != null) parentContext.addDistContents(src)
                else parentContext.log("!", "Cannot copy from another copy task `$it`, destPath is not defined")
            }
            it is File -> {
                parentContext.addDistContents(it.path)
            }
            else -> parentContext.logUnsupported("SOURCE PATH", it)
        }
    }
}