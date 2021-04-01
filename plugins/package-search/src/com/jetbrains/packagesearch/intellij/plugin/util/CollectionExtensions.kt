package com.jetbrains.packagesearch.intellij.plugin.util

internal fun <E> Collection<E>.anyIn(
    other: Collection<E>,
    predicate: (E, E) -> Boolean = { ourItem, otherItem -> ourItem == otherItem }
): Boolean {
    return any { ourItem -> other.any { otherItem -> predicate(ourItem, otherItem) } }
}
