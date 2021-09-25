// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.incremental.storages

import com.intellij.openapi.util.io.FileUtil

class PathFunctionPair(
    val path: String,
    val function: String
) : Comparable<PathFunctionPair> {
    override fun compareTo(other: PathFunctionPair): Int {
        val pathComp = FileUtil.comparePaths(path, other.path)

        if (pathComp != 0) return pathComp

        return function.compareTo(other.function)
    }

    override fun equals(other: Any?): Boolean =
        when (other) {
            is PathFunctionPair ->
                FileUtil.pathsEqual(path, other.path) && function == other.function
            else ->
                false
        }

    override fun hashCode(): Int = 31 * FileUtil.pathHashCode(path) + function.hashCode()
}
