@file:Suppress("PackageDirectoryMismatch")
package org.jetbrains.kotlin.pill

import EmbeddedComponents
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.file.copy.SingleParentCopySpec
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Copy
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.extra
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File

fun generateAllJpsArtifacts(rootProject: Project, project: Project) {
    File(".idea/artifacts/").listFiles().forEach {
        if (it.isFile && it.name.startsWith("gradle")) {
            it.delete()
        }
    }

    project.tasks.all {
        if (this is Copy) {
            generateJpsArtifact(rootProject, this)?.write()
        }
    }

    project.subprojects {
        generateAllJpsArtifacts(rootProject, this)
    }
}

fun generateJpsArtifact(rootProject: Project, task: Copy): PFile? {
    if (task is ProcessResources) return null

    println(task.identityPath.path)

    val gradleArtifactDir = File(rootProject.extra["ideaPluginDir"] as File, "lib")

    val taskFullId = task.identityPath.path
    val fileName = "gradle" + taskFullId.replace(':', '_')
    val root = ArtifactElement.Root()
    val artifact = PArtifact(taskFullId, java.io.File(rootProject.projectDir, "out/artifacts/$taskFullId"), root)

    val spec = task.rootSpec.children.filterIsInstance<SingleParentCopySpec>().singleOrNull()
            ?: error("$taskFullId: Copy spec is not unique. Available specs: ${task.rootSpec.children}")

    val sourcePaths = spec.sourcePaths
    for (sourcePath in sourcePaths) {
        if (sourcePath is ShadowJar) {
            if (sourcePath.project.path == ":prepare:idea-plugin") {
                val kotlinPluginJar = ArtifactElement.Archive(sourcePath.archiveName).also { root.getDirectory("lib").add(it) }

                kotlinPluginJar.add(ArtifactElement.FileCopy(java.io.File(rootProject.projectDir, "resources/kotlinManifest.properties")))

                for (jarFile in sourcePath.project.configurations.getByName("packedJars").resolve()) {
                    kotlinPluginJar.add(ArtifactElement.ExtractedDirectory(jarFile))
                }

                @Suppress("UNCHECKED_CAST")
                for (projectPath in sourcePath.project.extra["projectsToShadow"] as List<String>) {
                    val jpsModuleName = rootProject.findProject(projectPath)!!.name + ".src"
                    kotlinPluginJar.add(ArtifactElement.ModuleOutput(jpsModuleName))
                }

                continue
            }
        }

        when (sourcePath) {
            is Jar -> {
                val targetDir = ("lib/" + task.destinationDir.toRelativeString(gradleArtifactDir)).withoutSlash()

                val archiveForJar = ArtifactElement.Archive(sourcePath.project.name + ".jar").apply {
                    if (task.project.plugins.hasPlugin(JavaPlugin::class.java)) {
                        add(ArtifactElement.ModuleOutput(sourcePath.project.name + ".src"))
                    }
                    root.getDirectory(targetDir).add(this)
                }

                val embeddedComponents = sourcePath.project.configurations
                        .findByName(EmbeddedComponents.CONFIGURATION_NAME)?.resolvedConfiguration

                if (embeddedComponents != null) {
                    val configuration = CollectedConfiguration(embeddedComponents, POrderRoot.Scope.COMPILE)
                    for (dependencyInfo in listOf(configuration).collectDependencies()) {
                        val dependency = (dependencyInfo as? DependencyInfo.ResolvedDependencyInfo)?.dependency ?: continue

                        if (dependency.configuration == "runtimeElements") {
                            archiveForJar.add(ArtifactElement.ModuleOutput(dependency.moduleName + ".src"))
                        } else if (dependency.configuration == "tests-jar" || dependency.configuration == "jpsTest") {
                            error("Test configurations are not allowed here")
                        } else {
                            for (file in dependency.moduleArtifacts.map { it.file }) {
                                archiveForJar.add(ArtifactElement.ExtractedDirectory(file))
                            }
                        }
                    }
                }
            }
            is Configuration -> {
                require(sourcePath.name == "sideJars") { "Configurations other than 'sideJars' are not supported" }
                for (file in sourcePath.resolve()) {
                    root.getDirectory("lib").add(ArtifactElement.FileCopy(file))
                }
            }
            else -> println("${task.name} Unexpected task type ${task.javaClass.name}")
        }
    }

    return PFile(
            java.io.File(rootProject.projectDir, ".idea/artifacts/$fileName.xml"),
            artifact.render(ProjectContext(rootProject))
    )
}
