@Target(AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.VALUE_PARAMETER)
internal annotation class Ann1

@Target(AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.VALUE_PARAMETER)
internal annotation class Ann2

class C {
    fun f1() {
        @Ann1 val control = 0
        @Ann1 val arr = intArrayOf(1, 2, 3)
        for (@Ann1 test in arr) {
            println(control + test)
        }
        for (@Ann1 i in arr.indices) {
            println(control + arr[i])
        }
    }

    fun f2() {
        @Ann1 @Ann2 val control = 0
        @Ann1 @Ann2 val arr = intArrayOf(1, 2, 3)
        for (@Ann1 @Ann2 test in arr) {
            println(control + test)
        }
        for (@Ann1 @Ann2 i in arr.indices) {
            println(control + arr[i])
        }
    }
}
