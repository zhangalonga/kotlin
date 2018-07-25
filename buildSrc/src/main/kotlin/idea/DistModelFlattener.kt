package org.jetbrains.kotlin.buildUtils.idea

import idea.DistCopyDetailsMock
import org.gradle.api.Project

class DistModelFlattener(val rootProject: Project) {
    fun DistVFile.flatten(): DistVFile {
        val new = DistVFile(parent, name, file)
        copyFlattenedContentsTo(new)
        return new
    }

    private fun DistVFile.copyFlattenedContentsTo(new: DistVFile, inJar: Boolean = false) {
        contents.forEach {
            when (it) {
                is DistCopy -> {
                    val srcName = it.customTargetName ?: it.src.name
                    if (it.src.file.exists()) {
                        DistCopy(new, it.src, srcName)
                    }

                    if (!inJar && srcName.endsWith(".jar")) {
                        val newChild = new.getOrCreateChild(srcName)
                        it.src.copyFlattenedContentsTo(newChild, inJar = true)
                    } else {
                        it.src.copyFlattenedContentsTo(new, inJar)
                    }
                }
                is DistModuleOutput -> DistModuleOutput(new, it.projectId)
            }
        }

        child.values.forEach { oldChild ->
            if (inJar) {
                oldChild.copyFlattenedContentsTo(new, inJar = true)
            } else {
                val newChild = new.getOrCreateChild(oldChild.name)
                oldChild.copyFlattenedContentsTo(newChild)
            }
        }
    }
}

