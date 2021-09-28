// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import java.io.Serializable

interface KotlinPlatformContainer : Serializable, Iterable<KotlinPlatform> {
    /**
     * Distinct collection of Platforms.
     * Keeping 'Collection' as type for binary compatibility
     */
    val platforms: Collection<KotlinPlatform>
    val arePlatformsInitialized: Boolean

    @Deprecated(
        "Ambiguous semantics of 'supports' for COMMON or (ANDROID/JVM) platforms. Use 'platforms' directly to express clear intention",
        level = DeprecationLevel.ERROR
    )
    fun supports(simplePlatform: KotlinPlatform): Boolean

    @Deprecated(
        "Unclear semantics: Use 'platforms' directly to express intention",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("platforms.singleOrNull() ?: KotlinPlatform.COMMON")
    )
    fun getSinglePlatform() = platforms.singleOrNull() ?: KotlinPlatform.COMMON

    @Deprecated(
        "Unclear semantics: Use 'pushPlatform' instead",
        ReplaceWith("pushPlatform"),
        level = DeprecationLevel.ERROR
    )
    fun addSimplePlatforms(platforms: Collection<KotlinPlatform>) = pushPlatforms(platforms)

    /**
     * Adds the given [platforms] to this container.
     * Note: If any of the pushed [platforms] is common, then this container will drop all non-common platforms and subsequent invocations
     * to this function will have no further effect.
     */
    fun pushPlatforms(platforms: Iterable<KotlinPlatform>)

    /**
     * @see pushPlatforms
     */
    fun pushPlatforms(vararg platform: KotlinPlatform) {
        pushPlatforms(platform.toList())
    }

    override fun iterator(): Iterator<KotlinPlatform> {
        return platforms.toSet().iterator()
    }
}
