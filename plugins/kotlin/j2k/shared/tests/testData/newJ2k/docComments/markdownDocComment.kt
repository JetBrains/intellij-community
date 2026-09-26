/** A markdown doc comment. */
class C {
    /** A markdown doc comment
     * that spans several lines.
     *
     * See [C] for details. */
    fun foo() {
    }

    /** Description of the method.
     *
     * @param i the **first** parameter
     */
    fun bar(i: Int) {
    }

    /** Passes when `a < b` and when a < b && c > d without backticks. */
    fun angleBrackets() {
    }

    /** See [the docs][ref] and [C].
     *
     * [ref]: https://example.com */
    fun referenceLink() {
    }

    fun baz() {
        //// four slashes are not a doc comment
    }
}
