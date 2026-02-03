// "Configure arguments for the feature: break continue in inline lambdas" "true"
// LANGUAGE_VERSION: 2.1
// APPLY_QUICKFIX: false
// DISABLE_K2_ERRORS

fun test() {
    for (i in 1..10) {
        hof {
            break<caret>
        }
    }
}

inline fun hof(fn: () -> Unit) {
    while (true) {
        fn()
    }
}
