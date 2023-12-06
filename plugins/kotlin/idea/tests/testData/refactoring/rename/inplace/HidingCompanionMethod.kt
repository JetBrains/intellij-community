// NEW_NAME: compFun
// RENAME: member
class MemberExtract2 {
    var mainVar = 1
    fun main<caret>Fun() = 2

    companion object {
        var compVar = 7
        fun compFun() = 8
    }

    fun mainContext() {
        println(mainVar + mainFun() + compVar + compFun())
    }
}
// IGNORE_K1