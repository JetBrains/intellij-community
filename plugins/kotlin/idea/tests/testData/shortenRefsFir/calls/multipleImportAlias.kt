package test

import com.dependency.bar as bar1
import com.dependency.bar as bar2
import com.dependency.bar as bar3

fun foo(a: Int) = <selection>when (a) {
    1 -> bar1
    2 -> com.dependency.bar
    3 -> bar3
    else -> com.dependency.bar
}</selection>