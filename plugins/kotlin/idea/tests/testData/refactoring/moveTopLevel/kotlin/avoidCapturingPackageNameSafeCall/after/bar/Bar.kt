package bar

import foo.Other

fun bar(other: Other?) {
    other?.other()
    Other()?.other()
}