// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.welcomeScreen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val PyCharmWelcomeRightTabDarkBackground: ImageVector
  get() {
    if (_PyCharmWelcomeRightTabDarkBackground != null) {
      return _PyCharmWelcomeRightTabDarkBackground!!
    }
    _PyCharmWelcomeRightTabDarkBackground = ImageVector.Builder(
      name = "PyCharmWelcomeRightTabDarkBackground",
      defaultWidth = 1094.dp,
      defaultHeight = 847.dp,
      viewportWidth = 1094f,
      viewportHeight = 847f
    ).apply {
      group(
        clipPathData = PathData {
          moveTo(0f, 0f)
          horizontalLineToRelative(1094f)
          verticalLineToRelative(847f)
          horizontalLineToRelative(-1094f)
          close()
        }
      ) {
        path(
          fill = SolidColor(Color(0xFF00C4F4)),
          fillAlpha = 0.5f,
          strokeAlpha = 0.5f,
          pathFillType = PathFillType.EvenOdd
        ) {
          moveTo(539.9f, -325f)
          curveTo(689.8f, -325f, 689.8f, -275f, 689.8f, -175.1f)
          lineTo(689.8f, -50.1f)
          curveTo(689.8f, -8.7f, 656.2f, 24.9f, 614.8f, 24.9f)
          horizontalLineTo(464.9f)
          curveTo(395.9f, 24.9f, 339.9f, 80.8f, 339.9f, 149.8f)
          verticalLineTo(174.8f)
          curveTo(240f, 174.8f, 190f, 174.8f, 190f, 24.9f)
          curveTo(190f, -125.1f, 240f, -125.1f, 339.9f, -125.1f)
          lineTo(514.9f, -125.1f)
          curveTo(528.7f, -125.1f, 539.9f, -136.3f, 539.9f, -150.1f)
          curveTo(539.9f, -163.9f, 528.7f, -175.1f, 514.9f, -175.1f)
          horizontalLineTo(389.9f)
          curveTo(389.9f, -275f, 389.9f, -325f, 539.9f, -325f)
          close()
          moveTo(464.9f, -225f)
          curveTo(478.7f, -225f, 489.9f, -236.2f, 489.9f, -250f)
          curveTo(489.9f, -263.8f, 478.7f, -275f, 464.9f, -275f)
          curveTo(451.1f, -275f, 439.9f, -263.8f, 439.9f, -250f)
          curveTo(439.9f, -236.2f, 451.1f, -225f, 464.9f, -225f)
          close()
        }
        path(
          fill = SolidColor(Color(0xFFFFFC7B)),
          fillAlpha = 0.5f,
          strokeAlpha = 0.5f,
          pathFillType = PathFillType.EvenOdd
        ) {
          moveTo(754.1f, -110.8f)
          verticalLineTo(-35.8f)
          curveTo(754.1f, 33.2f, 698.1f, 89.1f, 629.1f, 89.1f)
          horizontalLineTo(479.2f)
          curveTo(437.8f, 89.1f, 404.2f, 122.7f, 404.2f, 164.1f)
          lineTo(404.2f, 239.1f)
          curveTo(404.2f, 339f, 404.2f, 389f, 554.1f, 389f)
          curveTo(704.1f, 389f, 704.1f, 339f, 704.1f, 239.1f)
          lineTo(579.1f, 239.1f)
          curveTo(565.3f, 239.1f, 554.1f, 227.9f, 554.1f, 214.1f)
          curveTo(554.1f, 200.3f, 565.3f, 189.1f, 579.1f, 189.1f)
          lineTo(754.1f, 189.1f)
          curveTo(854f, 189.1f, 904f, 189.1f, 904f, 39.1f)
          curveTo(904f, -110.8f, 854f, -110.8f, 754.1f, -110.8f)
          close()
          moveTo(629.1f, 339f)
          curveTo(642.9f, 339f, 654.1f, 327.8f, 654.1f, 314f)
          curveTo(654.1f, 300.2f, 642.9f, 289f, 629.1f, 289f)
          curveTo(615.3f, 289f, 604.1f, 300.2f, 604.1f, 314f)
          curveTo(604.1f, 327.8f, 615.3f, 339f, 629.1f, 339f)
          close()
        }
      }
    }.build()

    return _PyCharmWelcomeRightTabDarkBackground!!
  }

@Suppress("ObjectPropertyName")
private var _PyCharmWelcomeRightTabDarkBackground: ImageVector? = null
