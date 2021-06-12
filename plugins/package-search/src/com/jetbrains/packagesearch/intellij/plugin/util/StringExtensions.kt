package com.jetbrains.packagesearch.intellij.plugin.util

internal fun String?.nullIfBlank(): String? = if (isNullOrBlank()) null else this

private val nonWordCharacterRegex = "\\W".toRegex()

internal fun String.replaceNonWordCharactersWithSpaces(): String = replace(nonWordCharacterRegex, " ")

internal fun String.makeBreakableAroundNonWordCharacters(): String = replace(nonWordCharacterRegex) {
    "\u200B${it.value}\u200B" // Inserting zero-width spaces around
}
