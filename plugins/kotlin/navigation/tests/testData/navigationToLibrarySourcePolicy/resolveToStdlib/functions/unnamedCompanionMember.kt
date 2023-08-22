fun a() {
    CharProgression.fromClosed<caret>Range('a', 'b', 1)
}

// REF: (kotlin.ranges.CharProgression.Companion.fromClosedRange) public fun fromClosedRange(rangeStart: Char, rangeEnd: Char, step: Int): CharProgression