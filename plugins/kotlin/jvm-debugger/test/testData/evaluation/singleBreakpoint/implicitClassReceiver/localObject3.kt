fun main() {
    val obj = object {
        val bar: String
            get() {
                // EXPRESSION: this
                // RESULT: instance of LocalObject3Kt$main$obj$1(id=ID): LLocalObject3Kt$main$obj$1;
                //Breakpoint!
                return "bar"
            }
    }

    obj.bar
}
