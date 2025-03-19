// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.filter

import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.execution.filters.Filter
import com.intellij.execution.impl.InlayProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.python.PyBundle
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.packaging.PyPackageInstallUtils
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent

class InstallPackageButtonItem(
  val project: Project,
  val pythonSdk: Sdk,
  offset: Int,
  private val packageName: String,
) : Filter.ResultItem(offset, offset, null), InlayProvider {

  override fun createInlayRenderer(editor: Editor): EditorCustomElementRenderer {
    val factory = PresentationFactory(editor)
    val basePresentation = factory.referenceOnHover(
      factory.roundWithBackgroundAndSmallInset(
        factory.seq(
          factory.icon(PythonIcons.Python.PythonPackages),
          factory.inset(factory.smallText(" ${PyBundle.message("filter.install.package")}"), top = 2)
        ),
      )
    ) { event: MouseEvent?, _: Point ->
      val component = event?.component ?: return@referenceOnHover
      val relativePoint = RelativePoint(component, event.point)
      PyPackageInstallUtils.invokeInstallPackage(project, pythonSdk, packageName, relativePoint)
    }
    val presentation = factory.withCursorOnHover(basePresentation, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
    return PresentationRenderer(presentation)
  }


}


