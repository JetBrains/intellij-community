internal class C {
    fun test1(obj: Any?) {
        if (obj is String) {
            println("String" + obj.hashCode()) // give up
        }
    }

    fun test2(obj: Any) {
        if (obj is String) {
            println("String" + obj.hashCode()) // give up
        } else {
            println("Object" + obj.hashCode()) // not-null
        }
    }

    fun test3(obj: Any) {
        if (obj !is String) {
            println("Not String" + obj.hashCode()) // not-null
        }
    }

    fun test4(obj: Any?) {
        if (obj !is String) {
            println("Not String")
        } else {
            println("String" + obj.hashCode()) // give up (like in test1)
        }
    }

    fun test5(obj: Any) {
        if (obj is String) {
            println("String" + obj.hashCode()) // give up
        }
        println("Object" + obj.hashCode()) // not-null
    }

    fun test6(obj: Any, b: Boolean) {
        if (b || obj is String) {
            println(obj.hashCode()) // not-null
        } else {
            println(obj.hashCode()) // not-null
        }
    }

    fun test7(obj: Any, b: Boolean) {
        if (b && obj is String) {
            println(obj.hashCode()) // give up
        } else {
            println(obj.hashCode()) // not-null
        }
    }
}
