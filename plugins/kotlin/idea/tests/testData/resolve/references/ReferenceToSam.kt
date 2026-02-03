fun some() {
    val jClass = JavaTest.SomeJavaClass()
    jClass.<caret>setListener {}
}

// REF: of JavaTest.SomeJavaClass.setListener(SAMInterface)
