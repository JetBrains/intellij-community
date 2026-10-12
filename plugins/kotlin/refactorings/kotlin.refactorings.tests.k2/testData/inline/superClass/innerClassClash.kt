// ERROR: Class Child already contains nested class named <b><code>Reader</code></b>
open class <caret>Base {
    inner class Reader
}

class Child : Base() {
    inner class Reader
}
