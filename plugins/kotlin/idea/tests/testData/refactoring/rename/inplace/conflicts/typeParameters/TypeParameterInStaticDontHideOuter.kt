// NEW_NAME: OT
// RENAME: member
package rename

class P<OT> {
    class Nested<<caret>K> {
        fun foo() {}
        class NestedNested<OT>
    }
}