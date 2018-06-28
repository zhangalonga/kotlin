package breakpointWhileStepOver

fun main(args: Array<String>) {
    f(2)
}

fun f(count: Int) {
    if (count > 0) {
        //Breakpoint!
        block(count)
    }
}

fun block(count: Int) {
    println("hello")
    //Breakpoint!
    f(count - 1)
}

// STEP_OVER: 20