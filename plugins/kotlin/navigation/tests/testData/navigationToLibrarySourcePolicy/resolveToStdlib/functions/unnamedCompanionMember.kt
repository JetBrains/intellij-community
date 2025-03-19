fun a() {
    CharProgression.fromClosed<caret>Range('a', 'b', 1)
}

// REF: (kotlin.ranges.CharProgression.Companion @ jar://kotlin-stdlib-sources.jar!/commonMain/kotlin/ranges/Progressions.kt) public fun fromClosedRange(rangeStart: Char, rangeEnd: Char, step: Int): CharProgression