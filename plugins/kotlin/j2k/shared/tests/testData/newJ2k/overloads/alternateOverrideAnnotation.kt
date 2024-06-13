// ERROR: Unresolved reference: foo
import foo.bar.Override

class Test {
    @Override
    fun notAnOverride(savedInstanceState: String?) {
        println("hi")
    }
}
