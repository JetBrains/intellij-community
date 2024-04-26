package rename

class X {
    class /*rename*/JavaConflict {
        val b: JavaConflict = JavaConflict()
        val j: rename.JavaConflict = rename.JavaConflict()
    }
}
