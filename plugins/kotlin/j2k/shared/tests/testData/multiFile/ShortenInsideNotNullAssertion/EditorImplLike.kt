package test2

import test.EditorThreading.Companion.compute
import test.ThrowableComputable
import test.View

class EditorImplLike {
    private val myView = View()

    val visibleLineCount: Int
        get() = compute<Int?, RuntimeException>(ThrowableComputable { myView.getVisibleLineCount() })!!

    fun lineHeight(): Int {
        return compute<Int?, RuntimeException>(ThrowableComputable { myView.getLineHeight() })!!
    }

    fun boxedLineHeight(): Int? {
        return compute<Int?, RuntimeException>(ThrowableComputable { myView.getLineHeight() })
    }

    val boxedVisibleLineCount: Int?
        get() = compute<Int?, RuntimeException>(ThrowableComputable { myView.getVisibleLineCount() })

    fun takeInt(x: Int) {
    }

    fun argumentCase() {
        takeInt(compute<Int?, RuntimeException>(ThrowableComputable { myView.getLineHeight() })!!)
    }
}
