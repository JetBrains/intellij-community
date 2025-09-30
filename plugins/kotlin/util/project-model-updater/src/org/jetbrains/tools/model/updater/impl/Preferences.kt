// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater.impl

import java.util.*
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

abstract class Preferences(private val properties: Properties) {
    protected class MandatoryPreference<T : Any>(private val mapper: (String) -> T?) : ReadOnlyProperty<Preferences, T> {
        companion object {
            operator fun invoke(): MandatoryPreference<String> = MandatoryPreference { it }
        }

        override fun getValue(thisRef: Preferences, property: KProperty<*>): T {
            val key = property.name
            val rawValue = thisRef.properties.getProperty(key) ?: error("Property \"$key\" not found")
            return mapper(rawValue) ?: error("Property \"$key\" contains invalid value")
        }
    }

    protected class OptionalPreference<T: Any>(private val mapper: (String) -> T?) : ReadOnlyProperty<Preferences, T?> {
        override fun getValue(thisRef: Preferences, property: KProperty<*>): T? {
            val key = property.name
            val rawValue = thisRef.properties.getProperty(key) ?: return null
            return mapper(rawValue) ?: error("Property \"$key\" contains invalid value")
        }
    }
}
