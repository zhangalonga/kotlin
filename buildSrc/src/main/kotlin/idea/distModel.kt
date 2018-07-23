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



sealed class DistElement(val parent: DistContainer?) {
    init {
        parent?.addChild(this)
    }
}

open class DistNamedElement(parent: DistContainer?, val name: String) : DistElement(parent)

open class DistContentElement(parent: DistContainer?) : DistElement(parent)

open class DistCopy(val src: DistElement)

class DistModuleOutput(parent: DistContainer?, val sourceSets: List<SourceSet>) : DistContentElement(parent)

class SourceSet(val dir: File)

class DistExistedFile(parent: DistContainer?, file: File, val newName: String = file.name) : DistNamedElement(parent, newName)

open class DistContainer(parent: DistContainer?, name: String) : DistNamedElement(parent, name) {
    val named = mutableMapOf<String, DistNamedElement>()
    val contents = mutableListOf<DistContentElement>()

    fun getOrCreateDirByPath(path: String) =
            path.split(File.pathSeparator).fold(this) { parent, childName ->
                parent.getOrCreateDir(childName)
            }

    fun getOrCreateDir(name: String): DistContainer {
        val child = named[name]

        return if (child != null) {
            child as? DistContainer ?: error("directory expected, but `$child` found")
        } else {
            DistContainer(this, name)
        }
    }

    fun addChild(child: DistElement) {
        when (child) {
            is DistNamedElement -> addNamedChild(child)
            is DistContentElement -> contents.add(child)
        }
    }

    private fun addNamedChild(child: DistNamedElement) {
        val prev = named.put(child.name, child)
        check(prev == null) { "Cannot add `$child`, element with this name already existed: `$prev`" }
    }
}

class DistDir(parent: DistContainer?, name: String) : DistContainer(parent, name) {

}

class DistJar(parent: DistContainer?, name: String) : DistContainer(parent, name) {

}