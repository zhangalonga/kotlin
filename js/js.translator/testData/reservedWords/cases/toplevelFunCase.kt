package foo

// NOTE THIS FILE IS AUTO-GENERATED by the generateTestDataForReservedWords.kt. DO NOT EDIT!

fun case() { case() }

fun box(): String {
    testNotRenamed("case", { case() })

    return "OK"
}