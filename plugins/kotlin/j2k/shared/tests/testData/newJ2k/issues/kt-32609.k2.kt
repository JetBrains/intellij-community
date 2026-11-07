// ERROR: 'compareTo' overrides nothing. Potential signatures for overriding: fun compareTo(other: Book?): Int
// ERROR: Class 'Book' is not abstract and does not implement abstract member: fun compareTo(other: T): Int
class Book : Comparable<Book?> {
    override fun compareTo(o: Book): Int {
        return 0
    }
}
