// K1_ACTION: "Specify override for 'add(String): Boolean' explicitly" "true"
// K2_ACTION: "Specify override for '@IgnorableReturnValue add(String): Boolean' explicitly" "true"
// WITH_STDLIB

import java.util.*

class <caret>B(private val f: MutableList<String>): ArrayList<String>(), MutableList<String> by f {
    override fun isEmpty(): Boolean {
        return f.isEmpty()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyOverrideExplicitlyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SpecifyOverrideExplicitlyFixFactory$SpecifyOverrideExplicitlyFix
