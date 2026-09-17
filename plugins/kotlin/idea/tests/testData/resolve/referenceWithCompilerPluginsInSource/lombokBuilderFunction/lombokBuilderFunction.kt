// COMPILER_PLUGIN_PRESET: LOMBOK

package test

fun usage() {
    User.<caret>builder()
}

// REF_EMPTY
// SKIP_IS_REFERENCE_TO_CHECK
