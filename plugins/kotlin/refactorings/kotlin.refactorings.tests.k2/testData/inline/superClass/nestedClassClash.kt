// ERROR: Class Child already contains nested class named <b><code>Nested</code></b>
open class <caret>Base {
    class Nested
}

class Child : Base() {
    class Nested
}
