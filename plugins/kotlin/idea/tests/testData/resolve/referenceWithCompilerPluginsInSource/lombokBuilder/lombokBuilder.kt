// COMPILER_PLUGIN_PRESET: LOMBOK

package test

fun usage() {
    User.builder().<caret>name("John").build()
}

// REF: of test.User.name
// SKIP_IS_REFERENCE_TO_CHECK
