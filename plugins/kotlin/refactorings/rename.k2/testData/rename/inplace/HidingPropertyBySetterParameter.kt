// NEW_NAME: p
// RENAME: member
class U {
    var named: Int = 0
        get() = field + p
        set(pa<caret>ram) { field = param + p }
    val p = 1
}