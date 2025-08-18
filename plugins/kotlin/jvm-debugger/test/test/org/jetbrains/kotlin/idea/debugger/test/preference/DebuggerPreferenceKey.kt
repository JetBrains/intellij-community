// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test.preference

import java.lang.reflect.ParameterizedType
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaType

class DebuggerPreferenceKey<T : Any>(val name: String, val type: Class<*>, val defaultValue: T)

private inline fun <reified T : Any> debuggerPreferenceKey(defaultValue: T): ReadOnlyProperty<Any, DebuggerPreferenceKey<T>> {
    return ReadOnlyProperty { _, property -> DebuggerPreferenceKey(property.name, T::class.java, defaultValue) }
}

internal object DebuggerPreferenceKeys {
    val SKIP_SYNTHETIC_METHODS by debuggerPreferenceKey(true)
    val SKIP_CONSTRUCTORS: DebuggerPreferenceKey<Boolean> by debuggerPreferenceKey(false)
    val SKIP_CLASSLOADERS by debuggerPreferenceKey(true)
    val TRACING_FILTERS_ENABLED by debuggerPreferenceKey(true)
    val SKIP_GETTERS by debuggerPreferenceKey(false)

    val DISABLE_KOTLIN_INTERNAL_CLASSES by debuggerPreferenceKey(false)
    val RENDER_DELEGATED_PROPERTIES by debuggerPreferenceKey(false)

    val FORCE_RANKING by debuggerPreferenceKey(false)

    val PRINT_FRAME by debuggerPreferenceKey(false)
    val SHOW_KOTLIN_VARIABLES by debuggerPreferenceKey(false)
    val DESCRIPTOR_VIEW_OPTIONS by debuggerPreferenceKey("FULL")

    val ATTACH_LIBRARY by debuggerPreferenceKey(emptyList<String>())
    val ATTACH_LIBRARY_BY_LABEL by debuggerPreferenceKey(emptyList<String>())
    val ATTACH_JAVA_AGENT_BY_LABEL by debuggerPreferenceKey(emptyList<String>())
    val ENABLED_LANGUAGE_FEATURE by debuggerPreferenceKey(emptyList<String>())

    val SKIP by debuggerPreferenceKey(emptyList<String>())
    val WATCH_FIELD_ACCESS by debuggerPreferenceKey(true)
    val WATCH_FIELD_MODIFICATION by debuggerPreferenceKey(true)
    val WATCH_FIELD_INITIALISATION by debuggerPreferenceKey(false)

    val JVM_TARGET by debuggerPreferenceKey("1.8")

    val REFLECTION_PATCHING by debuggerPreferenceKey(true)

    val SHOW_LIBRARY_STACK_FRAMES by debuggerPreferenceKey(true /* for backward compatibility with existing tests */)

    val REGISTRY by debuggerPreferenceKey(emptyList<String>())

    val JVM_DEFAULT_MODE by debuggerPreferenceKey("")

    val values: List<DebuggerPreferenceKey<*>> by lazy {
        DebuggerPreferenceKeys::class.declaredMemberProperties
            .filter { (it.returnType.javaType as? ParameterizedType)?.rawType == DebuggerPreferenceKey::class.java }
            .map { it.get(DebuggerPreferenceKeys) as DebuggerPreferenceKey<*> }
    }
}
