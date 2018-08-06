// FILE: A.java
public class A<T> {
    public T test() {
        return null;
    }
}


// FILE: main.kt

fun case_1(value1: Boolean, value2: Nothing): String {
    when {
        value1 -> return "1"
        value2 -> <!UNREACHABLE_CODE!>return "2"<!>
    }
}

fun main(args : Array<String>) {
    val test = A<Nothing>().test()

    case_1(true, <!DEBUG_INFO_CONSTANT!>test<!>)
}
