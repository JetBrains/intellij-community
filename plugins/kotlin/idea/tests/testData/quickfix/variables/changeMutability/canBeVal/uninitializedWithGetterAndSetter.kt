// "Change to 'val'" "false"
// WITH_STDLIB
// ERROR: Property must be initialized
// K2_AFTER_ERROR: Property must be initialized.
class Subject {
    var<caret> value: String
        get() = field
        set(input) {
            field = input.trim()
        }
}
