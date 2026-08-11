// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.filter

import com.intellij.codeInsight.hints.presentation.InlayButtonPresentationFactory
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
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.management.ui.launchInstallPackageWithBalloonBackground
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import java.awt.Cursor

class InstallPackageButtonItem(
  val project: Project,
  val pythonSdk: Sdk,
  offset: Int,
  private val packageName: String,
) : Filter.ResultItem(offset, offset, null), InlayProvider {
  override fun createInlayRenderer(editor: Editor): EditorCustomElementRenderer {
    val factory = PresentationFactory(editor)
    val inlayButtonPresentationFactory = InlayButtonPresentationFactory(editor, factory)
    val basePresentation =
      inlayButtonPresentationFactory
        .iconAndText(
          PythonIcons.Python.PythonPackages,
          PyBundle.message("filter.install.package")
        )
        .onClick { event, _ ->
          val component = event.component
          val relativePoint = RelativePoint(component, event.point)

          PyPackageCoroutine.launch(project) {
            PythonPackageManagerUI.forSdk(project, pythonSdk).launchInstallPackageWithBalloonBackground(packageName, relativePoint)
          }
        }
        .build(8)
    val presentation = factory.withCursorOnHover(basePresentation, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))

    return PresentationRenderer(presentation)
  }
}
