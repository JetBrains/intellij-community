// COMPILER_PLUGIN_PRESET: LOMBOK

package test

fun usage(user: User) {
    user.<caret>foo
}

// REF_EMPTY
// SKIP_IS_REFERENCE_TO_CHECK
