package pa

class Bar

class BasicOperatorMembers {
    infix operator fun plus(other: Bar): BasicOperatorMembers = this
    infix operator fun minus(other: Bar): BasicOperatorMembers = this
    infix operator fun times(other: Bar): BasicOperatorMembers = this
    infix operator fun div(other: Bar): BasicOperatorMembers = this
    infix operator fun rem(other: Bar): BasicOperatorMembers = this
}

class AugmentedOperatorMembers {
    infix operator fun plusAssign(other: Bar) {}
    infix operator fun minusAssign(other: Bar) {}
    infix operator fun timesAssign(other: Bar) {}
    infix operator fun divAssign(other: Bar) {}
    infix operator fun remAssign(other: Bar) {}
}

class BasicOperatorExtensions

infix operator fun BasicOperatorExtensions.plus(other: Bar): BasicOperatorExtensions = this
infix operator fun BasicOperatorExtensions.minus(other: Bar): BasicOperatorExtensions = this
infix operator fun BasicOperatorExtensions.times(other: Bar): BasicOperatorExtensions = this
infix operator fun BasicOperatorExtensions.div(other: Bar): BasicOperatorExtensions = this
infix operator fun BasicOperatorExtensions.rem(other: Bar): BasicOperatorExtensions = this

class AugmentedOperatorExtensions

infix operator fun AugmentedOperatorExtensions.plusAssign(other: Bar) {}
infix operator fun AugmentedOperatorExtensions.minusAssign(other: Bar) {}
infix operator fun AugmentedOperatorExtensions.timesAssign(other: Bar) {}
infix operator fun AugmentedOperatorExtensions.divAssign(other: Bar) {}
infix operator fun AugmentedOperatorExtensions.remAssign(other: Bar) {}
