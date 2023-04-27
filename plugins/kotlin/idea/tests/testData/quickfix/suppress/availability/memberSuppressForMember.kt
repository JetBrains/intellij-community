// "Suppress 'REDUNDANT_NULLABLE' for fun foo" "true"

class C {
    fun foo(): String?<caret>? = null
}

// IGNORE_FIR
