// "Suppress 'REDUNDANT_NULLABLE' for interface C" "true"

interface C {
    var foo: String?<caret>?
}

// IGNORE_FIR
