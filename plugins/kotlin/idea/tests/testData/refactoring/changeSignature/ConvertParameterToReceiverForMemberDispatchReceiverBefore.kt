// WITH_STDLIB
fun takeSource(source: Source) {}

class Source(val s: String = "") {
    fun <caret>sourceMemberWithTargetParam(param: Target) {
        takeSource(this)
    }

    companion object {
        fun sourceCompanion() {
            Source().sourceMemberWithTargetParam(Target())
        }
    }
}

fun Target.targetExtension() {
    Source().sourceMemberWithTargetParam(this)
}

fun targetParam(target: Target) {
    Source().sourceMemberWithTargetParam(target)
}

class Target(val t: String = "") {
    fun targetMember() {
        Source().sourceMemberWithTargetParam(this)
    }

    companion object {
        fun targetCompanion() {
            Source().sourceMemberWithTargetParam(Target())
        }
    }
}