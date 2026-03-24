// "Replace 'listOf(1)' with 'mutableListOf(1)'" "false"
// K2_ERROR: Return type mismatch: expected 'ArrayList<Int>', actual 'List<Int>'.
// K2_AFTER_ERROR: Return type mismatch: expected 'ArrayList<Int>', actual 'List<Int>'.
import java.util.ArrayList

fun foo(): ArrayList<Int> {
    return list<caret>Of(1)
}

// IGNORE_K1