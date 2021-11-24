// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.incremental

import java.nio.file.Path

private const val DATA_CONTAINER_VERSION_FILE_NAME = "data-container-format-version.txt"
private const val DATA_CONTAINER_VERSION = 6

fun lookupsCacheVersionManager(dataRoot: Path, isEnabled: Boolean) = CacheVersionManager(
    dataRoot.resolve(DATA_CONTAINER_VERSION_FILE_NAME),
    if (isEnabled) DATA_CONTAINER_VERSION else null
)