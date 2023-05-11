// "Suppress 'NOTHING_TO_INLINE' for fun run12" "true"

inline<caret> fun <T> run12(noinline f: () -> T): T = f()