// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import kotlin.reflect.KProperty

/**
 * Represents a modification of a preference from [GeneratorPreferences].
 */
internal class PreferenceModification<T>(val preference: KProperty<T>, val newValue: T) {
    val oldValue: T get() = preference.getter.call()

    val asOldProperty: String get() = asPropertyRepresentations(useNewValue = false)
    val asNewProperty: String get() = asPropertyRepresentations(useNewValue = true)

    private fun asPropertyRepresentations(useNewValue: Boolean): String = "${preference.name}=${if (useNewValue) newValue else oldValue}"

    override fun toString(): String = "${preference.name}='$oldValue' -> '$newValue'"
}

internal fun <T> KProperty<T>.modify(
    // Infer the type information only from the receiver to require the argument of the same type
    @Suppress("INVISIBLE_REFERENCE") newValue: @kotlin.internal.NoInfer T
): PreferenceModification<T> = PreferenceModification(this, newValue)

internal fun main(args: List<PreferenceModification<*>>) {
    main(args.map(PreferenceModification<*>::asNewProperty).toTypedArray())
}
