interface I {
    suspend fun doSomething()
}

suspend fun dummy() {}

suspend inline fun test() {
    val o = object : I {
        override suspend fun doSomething() {}
    }
    dummy()
    o.doSomething()
}