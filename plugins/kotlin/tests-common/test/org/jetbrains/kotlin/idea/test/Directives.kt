// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils

class Directives {

    private val directives = mutableMapOf<String, MutableList<String>?>()

    operator fun contains(key: String): Boolean {
        return key in directives
    }

    operator fun get(key: String): String? {
        return directives[key]?.single()
    }

    fun getValue(key: String): String {
        val values = directives[key]
        return when {
            values == null -> error("'$key' is not found")
            values.size > 1 -> error("Too many '$key' directives")
            else -> values.single()
        }
    }

    fun getBooleanValue(key: String): Boolean =
        contains(key) && getValue(key) == "TRUE"

    fun put(key: String, value: String?) {
        if (value == null) {
            directives[key] = null
        } else {
            directives.getOrPut(key) { arrayListOf() }.let {
                it?.add(value) ?: error("Null value was already passed to $key via smth like // $key")
            }
        }
    }

    // Such values could be defined several times, e.g
    // MY_DIRECTIVE: XXX
    // MY_DIRECTIVE: YYY
    // or
    // MY_DIRECTIVE: XXX, YYY
    fun listValues(name: String): List<String>? {
        return directives[name]?.let { values ->
            values.flatMap { InTextDirectivesUtils.splitValues(arrayListOf(), it) }
        }
    }
}