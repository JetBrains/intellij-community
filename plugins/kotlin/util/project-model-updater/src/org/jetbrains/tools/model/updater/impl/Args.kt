package org.jetbrains.tools.model.updater.impl

import kotlin.properties.ReadOnlyProperty

data class Args(val args: Map<String, String>) {
    val kotlincVersion: String by args
    val kotlincArtifactsMode: KotlincArtifactsMode by mapDelegate(args, KotlincArtifactsMode::valueOf)

    private fun <T> mapDelegate(map: Map<String, String>, transform: (String) -> T) =
        ReadOnlyProperty<Any?, T> { thisRef, property ->
            map[property.name]?.let(transform) ?: error("Cannot find ${property.name} in $map}")
        }
}

enum class KotlincArtifactsMode {
    MAVEN, BOOTSTRAP
}
