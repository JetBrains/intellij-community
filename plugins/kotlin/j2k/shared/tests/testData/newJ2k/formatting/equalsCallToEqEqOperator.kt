internal class C {
    fun test1(key: String): Boolean {
        return key == "one" // comment 1
                || key == "two" // comment 2
    }

    fun test2(key: String): Boolean {
        return key == "one" ||  // comment 1
                key == "two" // comment 2
    }

    fun test3(key: String): Boolean {
        return key == "one" || key == "two" // comment
    }
}
