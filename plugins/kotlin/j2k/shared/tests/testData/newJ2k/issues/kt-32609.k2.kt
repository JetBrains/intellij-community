// ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
// ERROR: NOTHING_TO_OVERRIDE
class Book : Comparable<Book?> {
    override fun compareTo(o: Book): Int {
        return 0
    }
}
