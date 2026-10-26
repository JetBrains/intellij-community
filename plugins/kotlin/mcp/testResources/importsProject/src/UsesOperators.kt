package sample

class UsesOperators {
    var field: NoSuchTypeAnywhere? = null

    fun check(value: Any?, flag: Boolean): Boolean {
        val text = value as? String ?: return false
        val other = value as String
        return flag && text.isEmpty() || other is CharSequence && value !is Int
    }
}
