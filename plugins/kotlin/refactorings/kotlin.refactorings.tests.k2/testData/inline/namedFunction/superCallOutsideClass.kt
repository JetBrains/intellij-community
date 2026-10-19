// INLINE_METHOD_THIS: true
// ERROR: Inlining this call would change the target of a super call.
open class ShortcutParent {
    open fun referSuper() = 100
}

class Shortcut : ShortcutParent() {
    override fun referSuper() = super.referSuper()
}

fun callShortcut(shortcut: Shortcut) {
    val value = shortcut.<caret>referSuper()
}
