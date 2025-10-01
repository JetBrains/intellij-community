// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater.impl

import org.jetbrains.tools.model.updater.exitWithErrorMessage
import java.util.*
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

abstract class Preferences(private val properties: Properties) {
    protected open class MandatoryPreference<T : Any>(private val mapper: (String) -> T?) : ReadOnlyProperty<Preferences, T> {
        companion object : MandatoryPreference<String>({ it })

        override fun getValue(thisRef: Preferences, property: KProperty<*>): T {
            val key = property.name
            val rawValue = thisRef.properties.getProperty(key) ?: exitWithErrorMessage("Property \"$key\" not found")
            return mapper(rawValue) ?: exitWithErrorMessage("Property \"$key\" contains invalid value (\"$rawValue\"")
        }
    }

    protected open class OptionalPreference<T : Any>(private val mapper: (String) -> T?) : ReadOnlyProperty<Preferences, T?> {
        override fun getValue(thisRef: Preferences, property: KProperty<*>): T? {
            val key = property.name
            val rawValue = thisRef.properties.getProperty(key) ?: return null
            return mapper(rawValue) ?: exitWithErrorMessage("Property \"$key\" contains invalid value (\"$rawValue\"")
        }

        companion object : OptionalPreference<String>({ it })
    }
}
