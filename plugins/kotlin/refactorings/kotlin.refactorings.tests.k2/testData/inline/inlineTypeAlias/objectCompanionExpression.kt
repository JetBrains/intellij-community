typealias <caret>HostCompanion = Host.Companion

class Host {
    companion object {
        fun member() = "Host"
    }
}

fun test() {
    val companion = HostCompanion
    println(HostCompanion.member())
    println(companion)
}
