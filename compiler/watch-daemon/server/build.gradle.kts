
plugins {
    kotlin("jvm")
    id("jps-compatible")
}

jvmTarget = "1.6"

dependencies {
    compile(project(":compiler:cli"))
    compile(project(":compiler:daemon-common"))
    compile(project(":compiler:incremental-compilation-impl"))
    compile(project(":kotlin-build-common"))
    compile(commonDep("org.fusesource.jansi", "jansi"))
    compile(commonDep("org.jline", "jline"))
//    compile("io.methvin:directory-watcher:")
    compile(intellijCoreDep()) { includeJars("intellij-core") }
    compile(intellijDep()) { includeIntellijCoreJarDependencies(project) }
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}
