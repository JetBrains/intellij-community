// IGNORE_FIR

fun JavaClass.foo(javaClass: JavaClass) {
    print(javaClass.<caret>something)
    javaClass.<caret>something = 1
    javaClass.<caret>something += 1
    javaClass.<caret>something++
    --javaClass.<caret>something

    <caret>something++
    (<caret>something)++
    (<caret>something) = 1
    (javaClass.<caret>something) = 1
}

// MULTIRESOLVE
// REF1: of JavaClass.getSomething()
// REF2: of JavaClass.setSomething(int)
// REF3: of JavaClass.getSomething()
// REF3: of JavaClass.setSomething(int)
// REF4: of JavaClass.getSomething()
// REF4: of JavaClass.setSomething(int)
// REF5: of JavaClass.getSomething()
// REF5: of JavaClass.setSomething(int)
// REF6: of JavaClass.getSomething()
// REF6: of JavaClass.setSomething(int)
// REF7: of JavaClass.getSomething()
// REF7: of JavaClass.setSomething(int)
// REF8: of JavaClass.setSomething(int)
// REF9: of JavaClass.setSomething(int)
