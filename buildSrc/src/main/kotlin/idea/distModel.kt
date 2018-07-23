package org.jetbrains.kotlin.buildUtils.idea

import java.io.File


class BuildVFile(
        val parent: BuildVFile?,
        val name: String,
        val file: File = File(parent!!.file, name)
) {
    val child = mutableMapOf<String, BuildVFile>()

    val contents = mutableListOf<DistContentElement>()

    override fun toString(): String = name

    val hasContents: Boolean = file.exists() || contents.isNotEmpty()

    fun relativePath(path: String): BuildVFile {
        val pathComponents = path.split(File.separatorChar)
        return pathComponents.fold(this) { parent: BuildVFile, childName: String ->
            try {
                parent.getOrCreateChild(childName)
            } catch (t: Throwable) {
                throw Exception("Error while processing path `$path`, components: `$pathComponents`, element: `$childName`", t)
            }
        }
    }

    fun getOrCreateChild(name: String): BuildVFile = child.getOrPut(name) {
        BuildVFile(this, name)
    }

    fun addContents(contents: DistContentElement) {
        this.contents.add(contents)
    }
}

sealed class DistContentElement(val targetDir: BuildVFile)

///////

class DistCopy(parent: BuildVFile, val src: BuildVFile) : DistContentElement(parent) {
    init {
        parent.addContents(this)
    }
}

class DistExtractedCopy(parent: BuildVFile, val src: BuildVFile) : DistContentElement(parent) {
    init {
        parent.addContents(this)
    }
}

class DistModuleOutput(parent: BuildVFile, val ideaModuleName: String) : DistContentElement(parent) {
    init {
        parent.addContents(this)
    }
}