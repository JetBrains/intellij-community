package pa

class Bar

class BasicOperatorMembers {
    operator fun plus(other: Bar): BasicOperatorMembers = this
    operator fun minus(other: Bar): BasicOperatorMembers = this
    operator fun times(other: Bar): BasicOperatorMembers = this
    operator fun div(other: Bar): BasicOperatorMembers = this
    operator fun rem(other: Bar): BasicOperatorMembers = this
}

class AugmentedOperatorMembers {
    operator fun plusAssign(other: Bar) {}
    operator fun minusAssign(other: Bar) {}
    operator fun timesAssign(other: Bar) {}
    operator fun divAssign(other: Bar) {}
    operator fun remAssign(other: Bar) {}
}

class BasicOperatorExtensions

operator fun BasicOperatorExtensions.plus(other: Bar): BasicOperatorExtensions = this
operator fun BasicOperatorExtensions.minus(other: Bar): BasicOperatorExtensions = this
operator fun BasicOperatorExtensions.times(other: Bar): BasicOperatorExtensions = this
operator fun BasicOperatorExtensions.div(other: Bar): BasicOperatorExtensions = this
operator fun BasicOperatorExtensions.rem(other: Bar): BasicOperatorExtensions = this

class AugmentedOperatorExtensions

operator fun AugmentedOperatorExtensions.plusAssign(other: Bar) {}
operator fun AugmentedOperatorExtensions.minusAssign(other: Bar) {}
operator fun AugmentedOperatorExtensions.timesAssign(other: Bar) {}
operator fun AugmentedOperatorExtensions.divAssign(other: Bar) {}
operator fun AugmentedOperatorExtensions.remAssign(other: Bar) {}
