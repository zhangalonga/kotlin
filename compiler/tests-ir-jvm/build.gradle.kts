plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    testRuntime(intellijDep())
    testCompile(projectTests(":compiler:tests-common"))
    testCompile(project(":kotlin-script-runtime"))
    testCompile(project(":kotlin-stdlib"))
    testCompile(project(":kotlin-test:kotlin-test-jvm"))
}

sourceSets {
    "main" { }
    "test" { projectDefault() }
}

projectTest {
    workingDir = rootDir
    maxParallelForks = Math.max(Runtime.getRuntime().availableProcessors() / 2, 1)
}
