// "Suppress 'REDUNDANT_NULLABLE' for file ${file}" "true"

public fun foo(): String?<caret>? = null

// IGNORE_FIR
