
description = "Sample Kotlin JSR 223 scripting jar with local (in-process) compilation and evaluation"

plugins {
    kotlin("jvm")
}

dependencies {
    compile(projectDist(":kotlin-stdlib"))
    compile(project(":kotlin-script-runtime"))
    compile(projectRuntimeJar(":kotlin-compiler-embeddable"))
    compile(project(":kotlin-script-util"))
    testCompile(projectDist(":kotlin-test:kotlin-test-junit"))
    testCompileOnly(project(":compiler:cli-common"))
    testCompileOnly(project(":compiler:daemon-common"))
    testCompile(commonDep("junit:junit"))
    testRuntime(projectDist(":kotlin-reflect"))
}

projectTest()
