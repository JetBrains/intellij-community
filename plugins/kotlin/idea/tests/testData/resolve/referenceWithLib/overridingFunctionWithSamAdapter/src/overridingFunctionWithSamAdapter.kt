package test

class MyClass: Test() {

}

fun test(m: MyClass) {
    m.ac<caret>t {
        "foo"
    }
}

// REF: of test.Test.act(Action)