// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ui

import com.intellij.ui.LayeredIcon
import com.intellij.util.IconUtil
import javax.swing.Icon
import javax.swing.SwingConstants

/** How much of the base icon a badge takes up in its corner. */
private const val BADGE_SCALE: Float = 0.5f

/**
 * [icon] with [badge] scaled down and pinned to its bottom-right corner, so that the base icon stays recognizable.
 */
internal fun badgeIcon(icon: Icon, badge: Icon): Icon =
  LayeredIcon(2).apply {
    setIcon(icon, 0)
    setIcon(IconUtil.scale(badge, null, BADGE_SCALE), 1, SwingConstants.SOUTH_EAST)
  }
