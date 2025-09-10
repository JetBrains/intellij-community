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

val PyCharmWelcomeRightTabLightBackground: ImageVector
  get() {
    if (_PyCharmWelcomeRightTabLightBackground != null) {
      return _PyCharmWelcomeRightTabLightBackground!!
    }
    _PyCharmWelcomeRightTabLightBackground = ImageVector.Builder(
      name = "PyCharmWelcomeRightTabLightBackground",
      defaultWidth = 1094.dp,
      defaultHeight = 878.dp,
      viewportWidth = 1094f,
      viewportHeight = 878f
    ).apply {
      group(
        clipPathData = PathData {
          moveTo(0f, 0f)
          horizontalLineToRelative(1094f)
          verticalLineToRelative(1117f)
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
          moveTo(539.9f, -324f)
          curveTo(689.8f, -324f, 689.8f, -274f, 689.8f, -174.1f)
          lineTo(689.8f, -49.1f)
          curveTo(689.8f, -7.7f, 656.2f, 25.9f, 614.8f, 25.9f)
          horizontalLineTo(464.9f)
          curveTo(395.9f, 25.9f, 339.9f, 81.8f, 339.9f, 150.8f)
          verticalLineTo(175.8f)
          curveTo(240f, 175.8f, 190f, 175.8f, 190f, 25.9f)
          curveTo(190f, -124.1f, 240f, -124.1f, 339.9f, -124.1f)
          lineTo(514.9f, -124.1f)
          curveTo(528.7f, -124.1f, 539.9f, -135.3f, 539.9f, -149.1f)
          curveTo(539.9f, -162.9f, 528.7f, -174.1f, 514.9f, -174.1f)
          horizontalLineTo(389.9f)
          curveTo(389.9f, -274f, 389.9f, -324f, 539.9f, -324f)
          close()
          moveTo(464.9f, -224f)
          curveTo(478.7f, -224f, 489.9f, -235.2f, 489.9f, -249f)
          curveTo(489.9f, -262.8f, 478.7f, -274f, 464.9f, -274f)
          curveTo(451.1f, -274f, 439.9f, -262.8f, 439.9f, -249f)
          curveTo(439.9f, -235.2f, 451.1f, -224f, 464.9f, -224f)
          close()
        }
        path(
          fill = SolidColor(Color(0xFFFFFC7B)),
          fillAlpha = 0.5f,
          strokeAlpha = 0.5f,
          pathFillType = PathFillType.EvenOdd
        ) {
          moveTo(754.1f, -109.8f)
          verticalLineTo(-34.8f)
          curveTo(754.1f, 34.2f, 698.1f, 90.1f, 629.1f, 90.1f)
          horizontalLineTo(479.2f)
          curveTo(437.8f, 90.1f, 404.2f, 123.7f, 404.2f, 165.1f)
          lineTo(404.2f, 240.1f)
          curveTo(404.2f, 340f, 404.2f, 390f, 554.1f, 390f)
          curveTo(704.1f, 390f, 704.1f, 340f, 704.1f, 240.1f)
          lineTo(579.1f, 240.1f)
          curveTo(565.3f, 240.1f, 554.1f, 228.9f, 554.1f, 215.1f)
          curveTo(554.1f, 201.3f, 565.3f, 190.1f, 579.1f, 190.1f)
          lineTo(754.1f, 190.1f)
          curveTo(854f, 190.1f, 904f, 190.1f, 904f, 40.1f)
          curveTo(904f, -109.8f, 854f, -109.8f, 754.1f, -109.8f)
          close()
          moveTo(629.1f, 340f)
          curveTo(642.9f, 340f, 654.1f, 328.8f, 654.1f, 315f)
          curveTo(654.1f, 301.2f, 642.9f, 290f, 629.1f, 290f)
          curveTo(615.3f, 290f, 604.1f, 301.2f, 604.1f, 315f)
          curveTo(604.1f, 328.8f, 615.3f, 340f, 629.1f, 340f)
          close()
        }
      }
    }.build()

    return _PyCharmWelcomeRightTabLightBackground!!
  }

@Suppress("ObjectPropertyName")
private var _PyCharmWelcomeRightTabLightBackground: ImageVector? = null
