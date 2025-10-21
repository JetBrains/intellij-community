// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codegen

import com.intellij.jarRepository.RemoteRepositoryDescription

/**
 * The 'google()' maven repository as seen in
 * ```
 * repositories {
 *     google()
 * }
 * ```
 *
 * This repository can be used to download compose-related artifacts.
 *
 * Note: This repository is proxied by the JetBrains cache-redirector.
 */
val googleMavenRepository = RemoteRepositoryDescription(
    "google", "Google Maven Repository",
    "https://cache-redirector.jetbrains.com/dl.google.com.android.maven2"
)
