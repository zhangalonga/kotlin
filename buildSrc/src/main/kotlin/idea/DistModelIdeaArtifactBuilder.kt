package org.jetbrains.kotlin.buildUtils.idea

import org.gradle.api.Project
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.gradle.ext.ArtifactType
import org.jetbrains.gradle.ext.RecursiveArtifact

class DistModelIdeaArtifactBuilder(val rootProject: Project) {
    fun RecursiveArtifact.addFiles(vFile: DistVFile, inJar: Boolean = false) {
        val files = mutableSetOf<String>()

        vFile.contents.forEach {
            when (it) {
                is DistCopy -> {
                    val file = it.src.file
                    when {
                        inJar && file.name.endsWith(".jar") -> extractedDirectory(file.path)
                        file.isDirectory -> {
                            files.add(file.name)
                            directoryContent(file.path)
                        }
                        else -> {
                            files.add(file.name)
                            file(file.path)
                        }
                    }
                }
                is DistModuleOutput -> {
                    val name = it.ideaModuleName

                    if (name != null) moduleOutput(name + "_main")
                    else println("Cannot find idea module name for project `${it.projectId}`")
                }
            }
        }

        vFile.child.values.forEach {
            if (it.name !in files) {
                when {
                    it.name.endsWith(".jar") -> archive(it.name).addFiles(it, true)
                    else -> directory(it.name).addFiles(it, inJar)
                }
            }
        }
    }

    val DistModuleOutput.ideaModuleName: String?
        get() {
            val findProject = rootProject.findProject(projectId)
            val idea = findProject?.extensions?.findByName("idea") as? IdeaModel
            val name = idea?.module?.name
            return name
        }
}