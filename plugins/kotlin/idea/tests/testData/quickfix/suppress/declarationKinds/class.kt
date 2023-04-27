// "Suppress 'REDUNDANT_NULLABLE' for class C" "true"

class C {
    var foo: String?<caret>? = null
}

// IGNORE_FIR
