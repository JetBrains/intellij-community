// "Specify override for 'isEmpty(): Boolean' explicitly" "true"
// WITH_STDLIB

import java.util.*

class <caret>B(f: MutableList<String>): ArrayList<String>(), MutableList<String> by f
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyOverrideExplicitlyFix