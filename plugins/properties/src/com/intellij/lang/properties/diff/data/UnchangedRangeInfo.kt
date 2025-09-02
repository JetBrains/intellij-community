// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.diff.data

import com.intellij.diff.util.Side

/**
 * Reduced version of [com.intellij.lang.properties.diff.MatchProcessor.NonSignificantChangeInfo] that contains only the range of unchanged lines and side.
 */
internal data class UnchangedRangeInfo(val range: SemiOpenLineRange, val side: Side)