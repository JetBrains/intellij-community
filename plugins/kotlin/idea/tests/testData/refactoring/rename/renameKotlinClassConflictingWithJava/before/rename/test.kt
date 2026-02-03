package rename

class X {
    class /*rename*/Bar {
        val b: Bar = Bar()
        val j: JavaConflict = JavaConflict()
    }
}
