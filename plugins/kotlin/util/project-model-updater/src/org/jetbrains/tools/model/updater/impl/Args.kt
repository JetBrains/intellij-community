package org.jetbrains.tools.model.updater.impl

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KVisibility

class Args(args: Map<String, String>) {
    private val mutableArgs: MutableMap<String, String> = HashMap(args)

    val kotlincVersion: String by mapDelegate(mutableArgs)
    val kotlincArtifactsMode: KotlincArtifactsMode by mapDelegate(mutableArgs, KotlincArtifactsMode::valueOf)

    init {
        this::class.members.filterIsInstance<KProperty<*>>()
            .filter { it.visibility == KVisibility.PUBLIC }
            .forEach { it.getter.call(this) } // Initialize all the values
        check(mutableArgs.isEmpty()) {
            "Unknown args: " + mutableArgs.map { (key, value) -> "$key=$value" }.joinToString()
        }
    }

    private fun mapDelegate(map: MutableMap<String, String>) = mapDelegate(map) { it }
    private fun <T> mapDelegate(map: MutableMap<String, String>, transform: (String) -> T) =
        object : ReadOnlyProperty<Any?, T> {
            private var value: T? = null

            override fun getValue(thisRef: Any?, property: KProperty<*>): T {
                return value ?: map.remove(property.name)?.let(transform).also { value = it }
                    ?: error("Cannot find ${property.name} in $map}")
            }
        }
}

enum class KotlincArtifactsMode {
    MAVEN, BOOTSTRAP
}
