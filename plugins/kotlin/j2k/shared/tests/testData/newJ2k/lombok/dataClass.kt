// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-12602: @Data generates a constructor and public accessors, so the class must keep both
package test

data class Pojo(val foo: Int, val bar: Long, val baz: String, var nonNull: String) {
    var withSetter: String? = null

    val initialized: String = "x"

    companion object {
        private const val CONSTANT = "c"
    }
}
