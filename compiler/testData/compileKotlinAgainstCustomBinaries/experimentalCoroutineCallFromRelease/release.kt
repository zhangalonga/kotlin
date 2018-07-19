suspend fun callRelease() {
    // TODO: Shall be error
    c()

    dummy()

    C().dummy()

    WithNested.Nested().dummy()

    WithInner().Inner().dummy()
}
