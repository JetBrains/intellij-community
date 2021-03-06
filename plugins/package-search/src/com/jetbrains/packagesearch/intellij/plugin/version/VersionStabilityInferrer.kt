package com.jetbrains.packagesearch.intellij.plugin.version

// NOTE: This file was copied from com.jetbrains.kpm.maven.wanderer.maven.index.version

import com.jetbrains.packagesearch.intellij.plugin.version.VersionTokenMatcher.Companion.regex
import com.jetbrains.packagesearch.intellij.plugin.version.VersionTokenMatcher.Companion.substring

private val singleLetterUnstableMarkerRegex = "\\b[abmt][.\\-]?\\d{1,3}\\w?\\b".toRegex(RegexOption.IGNORE_CASE) // E.g., a01, b-2, m.3a

internal fun looksLikeStableVersion(versionName: String): Boolean {
    if (versionName.isBlank()) return false

    if (singleLetterUnstableMarkerRegex.containsMatchIn(versionName)) {
        return false
    }

    val tokens = tokenizeVersionName(versionName)

    return tokens.none { token ->
        unstableTokens.any { matcher -> matcher.matches(token) }
    }
}

private fun tokenizeVersionName(versionName: String): List<String> {
    val tokens = mutableListOf<String>()

    var previousChar: Char? = null
    val tokenBuilder = StringBuilder(versionName.length)

    versionName.forEach { char ->
        if (previousChar != null && char.isTokenBoundary(previousChar!!)) {
            tokens += tokenBuilder.toString()
            tokenBuilder.clear()
        }
        tokenBuilder.append(char)
        previousChar = char
    }

    tokens += tokenBuilder.toString()

    return tokens.filter { token -> token.any { it.isLetterOrDigit() } }
}

private fun Char.isTokenBoundary(previousChar: Char): Boolean = when {
    !isLetterOrDigit() -> true
    isLetter() && !previousChar.isLetter() -> true
    isDigit() && !previousChar.isDigit() -> true
    else -> false
}

private val unstableTokens = listOf(
        substring("alpha"),
        substring("beta"),
        substring("bate"),
        substring("commit"),
        substring("unofficial"),
        substring("exp"),
        substring("experiment"),
        substring("experimental"),
        substring("milestone"),
        substring("deprecated"),
        substring("rc"),
        substring("rctest"),
        substring("cr"),
        substring("draft"),
        substring("ignored"),
        substring("test"),
        substring("placeholder"),
        substring("incubating"),
        substring("nightly"),
        substring("weekly"),
        regex("\\b(rel(ease)?[.\\-_]?)?candidate\\b".toRegex(RegexOption.IGNORE_CASE)),
        regex("\\br?dev(elop(ment)?)?\\b".toRegex(RegexOption.IGNORE_CASE)),
        regex("\\beap?\\b".toRegex(RegexOption.IGNORE_CASE)),
        regex("pre(view)?\\b".toRegex(RegexOption.IGNORE_CASE)),
        regex("\\bsnap(s?shot)?\\b".toRegex(RegexOption.IGNORE_CASE))
)

private sealed class VersionTokenMatcher {

    abstract fun matches(value: String): Boolean

    class SubstringMatcher(val toMatch: String) : VersionTokenMatcher() {

        private val toMatchLength = toMatch.length

        override fun matches(value: String): Boolean {
            val substringIndex = value.indexOf(toMatch, ignoreCase = true)
            if (substringIndex < 0) return false

            val afterSubstringIndex = substringIndex + toMatchLength
            val valueLength = value.length

            // Case 1. The value matches entirely
            if (substringIndex == 0 && afterSubstringIndex == valueLength) return true

            // Case 2. The match is at the beginning of value
            if (substringIndex == 0) {
                val nextLetter = value[afterSubstringIndex]
                return !nextLetter.isLetter() // Matching whole word
            }

            // Case 2. The match is at the end of value
            if (afterSubstringIndex == valueLength) {
                val previousLetter = value[substringIndex - 1]
                return !previousLetter.isLetterOrDigit() && previousLetter != '_' // Matching whole word
            }

            // Case 3. The match is somewhere inside of value
            val previousLetter = value[substringIndex - 1]
            val startsAtWordBoundary = !previousLetter.isLetterOrDigit() && previousLetter != '_'
            val nextLetter = value[afterSubstringIndex]
            val endsAtWordBoundary = !nextLetter.isLetter()

            return startsAtWordBoundary && endsAtWordBoundary // Needs to be matching a whole word
        }
    }

    class RegexMatcher(val regex: Regex) : VersionTokenMatcher() {

        override fun matches(value: String): Boolean = regex.containsMatchIn(value)
    }

    companion object {

        fun substring(toMatch: String) = SubstringMatcher(toMatch)

        fun regex(regex: Regex) = RegexMatcher(regex)
    }
}
