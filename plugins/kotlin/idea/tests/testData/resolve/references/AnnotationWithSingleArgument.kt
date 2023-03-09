package test

annotation class Annotation(val name: String)

@Annotation(<caret>"some")
class Some

// REF: (in test.Annotation).name
