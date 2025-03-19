
class A(val foo: Set<String>?) {
    fun bar(maybeFoo: String) {
        <selection>if (foo != null && foo.contains(maybeFoo)) println("not null")</selection>
    }
}
// IGNORE_K1