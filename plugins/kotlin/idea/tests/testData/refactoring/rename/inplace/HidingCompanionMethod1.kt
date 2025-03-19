// NEW_NAME: mainFun
// RENAME: member
class MemberExtract2 {
    var mainVar = 1
    fun mainFun() = 2

    companion object {
        var compVar = 7
        fun com<caret>pFun() = 8
    }

    fun mainContext() {
        println(mainVar + mainFun() + compVar + compFun())
    }
}
// IGNORE_K1