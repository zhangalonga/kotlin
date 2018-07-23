package org.jetbrains.kotlin.buildUtils.idea

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
                    if (it.src.file.exists()) {
                        DistCopy(new, it.src)
                    }

                    val srcName = it.src.name
                    if (!inJar && srcName.endsWith(".jar")) {
                        val newChild = new.getOrCreateChild(transformJarName(srcName))
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

    private fun transformJarName(name: String) =
            name.replace(Regex("-${java.util.regex.Pattern.quote(rootProject.version.toString())}"), "")
}

