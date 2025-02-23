// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.filter

import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.execution.filters.Filter
import com.intellij.execution.impl.InlayProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.packaging.PyPackageInstallUtils
import com.jetbrains.python.packaging.common.runPackagingOperationOrShowErrorDialog
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.statistics.PyPackagesUsageCollector
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.UIManager

class InstallPackageButtonItem(
  val project: Project,
  val noteEditor: EditorImpl? = null,
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
      installPackage(relativePoint)
    }
    val presentation = factory.withCursorOnHover(basePresentation, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
    return PresentationRenderer(presentation)
  }

  private fun installPackage(point: RelativePoint) {
    val sdk: Sdk = getSdkForFile() ?: project.pythonSdk ?: return

    PyPackageCoroutine.launch(project) {
      runPackagingOperationOrShowErrorDialog(sdk, PyBundle.message("python.new.project.install.failed.title", packageName),
                                             packageName) {
        val loadBalloon = showBalloon(point, PyBundle.message("python.packaging.installing.package", packageName), BalloonStyle.INFO)
        try {
          PyPackageInstallUtils.confirmAndInstall(project, sdk, packageName)
          loadBalloon.hide()
          PyPackagesUsageCollector.installPackageFromConsole.log(project)
          showBalloon(point, PyBundle.message("python.packaging.notification.description.installed.packages", packageName), BalloonStyle.SUCCESS)
        }
        catch (t: Throwable) {
          loadBalloon.hide()
          PyPackagesUsageCollector.failInstallPackageFromConsole.log(project)
          showBalloon(point, PyBundle.message("python.new.project.install.failed.title", packageName), BalloonStyle.ERROR)
          throw t
        }
        Result.success(Unit)
      }
    }
  }

  private fun getSdkForFile(): Sdk? {
    val document = noteEditor?.document ?: return null
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
    val viewProvider = psiFile?.viewProvider ?: return null
    val pyPsiFile = viewProvider.allFiles.firstOrNull { it is PyFile } ?: return null
    return PythonSdkUtil.findPythonSdk(pyPsiFile)
  }

  private fun showBalloon(point: RelativePoint, @NlsContexts.DialogMessage text: String, style: BalloonStyle): Balloon {
    val content = JLabel()
    val (borderColor, fillColor) = when (style) {
      BalloonStyle.SUCCESS -> JBUI.CurrentTheme.Banner.SUCCESS_BORDER_COLOR to JBUI.CurrentTheme.Banner.SUCCESS_BACKGROUND
      BalloonStyle.INFO -> JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR to JBUI.CurrentTheme.Banner.INFO_BACKGROUND
      BalloonStyle.ERROR -> JBUI.CurrentTheme.Validator.errorBorderColor() to JBUI.CurrentTheme.Validator.errorBackgroundColor()
    }
    val balloonBuilder = JBPopupFactory.getInstance()
      .createBalloonBuilder(content)
      .setBorderInsets(UIManager.getInsets("Balloon.error.textInsets"))
      .setBorderColor(borderColor)
      .setFillColor(fillColor)
      .setHideOnClickOutside(true)
      .setHideOnFrameResize(false)
    content.text = text
    val balloon = balloonBuilder.createBalloon()
    balloon.show(point, Balloon.Position.below)
    return balloon
  }

  enum class BalloonStyle { ERROR, INFO, SUCCESS }
}