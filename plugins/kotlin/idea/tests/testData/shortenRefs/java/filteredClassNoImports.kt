// IMPORT_NESTED_CLASSES: true
// CLASS_IMPORT_FILTER_VETO_REGEX: Other
// CLASS_IMPORT_FILTER_VETO_REGEX: B

fun bar(s: String) {
    <selection>val t: A.B = A().B(s)</selection>
}

