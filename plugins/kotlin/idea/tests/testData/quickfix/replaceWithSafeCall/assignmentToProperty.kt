// "Replace with safe (?.) call" "true"
// WITH_STDLIB
class T(s: String?) {
    var i: Int = s<caret>.length
}
/* FIR_COMPARISON */