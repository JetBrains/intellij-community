// WITH_STDLIB
fun takeSource(source: Source) {}

class Source(val s: String = "") {
    fun Target.sourceMemberWithTargetParam() {
        takeSource(this@Source)
    }

    companion object {
        fun sourceCompanion() {
            with(Source()) {
                Target().sourceMemberWithTargetParam()
            }
        }
    }
}

fun Target.targetExtension() {
    with(Source()) {
        this@targetExtension.sourceMemberWithTargetParam()
    }
}

fun targetParam(target: Target) {
    with(Source()) {
        target.sourceMemberWithTargetParam()
    }
}

class Target(val t: String = "") {
    fun targetMember() {
        with(Source()) {
            this@targetMember.sourceMemberWithTargetParam()
        }
    }

    companion object {
        fun targetCompanion() {
            with(Source()) {
                Target().sourceMemberWithTargetParam()
            }
        }
    }
}
