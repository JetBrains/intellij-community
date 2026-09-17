// COMPILER_PLUGIN_PRESET: LOMBOK

package test

fun usage(user: User) {
    user.<caret>foo = "value"
}

// REF: of test.User.foo
// SKIP_IS_REFERENCE_TO_CHECK
