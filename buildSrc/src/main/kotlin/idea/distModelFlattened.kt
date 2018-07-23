package idea

import org.gradle.api.Project
import org.jetbrains.kotlin.buildUtils.idea.BuildVFile
import org.jetbrains.kotlin.buildUtils.idea.DistCopy
import org.jetbrains.kotlin.buildUtils.idea.DistModuleOutput

class DistModelFlattener(val rootProject: Project) {
    fun BuildVFile.copyFlattenedContentsTo(new: BuildVFile, inJar: Boolean = false) {
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

    fun BuildVFile.flatten(): BuildVFile {
        val new = BuildVFile(parent, name, file)
        copyFlattenedContentsTo(new)
        return new
    }

    fun transformJarName(name: String) =
            name.replace(Regex("-${java.util.regex.Pattern.quote(rootProject.version.toString())}"), "")
}

