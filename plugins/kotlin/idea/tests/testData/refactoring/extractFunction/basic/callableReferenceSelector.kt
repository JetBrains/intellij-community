// WITH_STDLIB
// SUGGESTED_NAMES: map, stringStringMap, stringMap
// PARAM_DESCRIPTOR: value-parameter arg: kotlin.String defined in Foo.Companion.bar
// PARAM_TYPES: kotlin.String

class Foo {
    var arguments: Map<String, String>? = null

    companion object {
        fun bar(arg: String) = Foo().apply {
            arguments = <selection>mapOf(Foo::arguments.name to arg)</selection>
        }
    }
}