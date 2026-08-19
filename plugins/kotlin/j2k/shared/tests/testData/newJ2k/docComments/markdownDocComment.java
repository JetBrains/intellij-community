/// A markdown doc comment.
public class C {
    /// A markdown doc comment
    /// that spans several lines.
    ///
    /// See [C] for details.
    void foo() {
    }

    /// Description of the method.
    ///
    /// @param i the **first** parameter
    void bar(int i) {
    }

    /// Passes when `a < b` and when a < b && c > d without backticks.
    void angleBrackets() {
    }

    /// See [the docs][ref] and [C].
    ///
    /// [ref]: https://example.com
    void referenceLink() {
    }

    void baz() {
        //// four slashes are not a doc comment
    }
}
