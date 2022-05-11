package com.jetbrains.packagesearch.intellij.plugin.dependency.analyzer

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.getBestBalloonPosition
import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.services.DependencyNavigationService
import com.jetbrains.packagesearch.intellij.plugin.ui.services.NavigationResult
import javax.swing.JLabel

abstract class DependencyAnalyzerGoToPackageSearchAction : DumbAwareAction() {

    abstract val systemId: ProjectSystemId

    abstract fun getModule(e: AnActionEvent): Module?

    abstract fun getUnifiedCoordinates(e: AnActionEvent): UnifiedCoordinates?

    override fun actionPerformed(e: AnActionEvent) {
        val module = getModule(e) ?: return
        val coordinates = getUnifiedCoordinates(e) ?: return
        val navigationService = DependencyNavigationService.getInstance(module.project)
        val navigationResult = navigationService.navigateToDependency(module, coordinates)
        val navigationResultMessage = when (navigationResult) {
            is NavigationResult.CoordinatesNotFound -> PackageSearchBundle.message("packagesearch.actions.showToolWindow.not.found.dependency")
            is NavigationResult.DependencyNotFound -> PackageSearchBundle.message("packagesearch.actions.showToolWindow.not.found.dependency")
            is NavigationResult.ModuleNotSupported -> PackageSearchBundle.message("packagesearch.actions.showToolWindow.not.found.module")
            NavigationResult.Success -> null
        }
        if (navigationResultMessage != null) {
            showBalloon(e.dataContext, navigationResultMessage)
        }
    }

    private fun showBalloon(dataContext: DataContext, message: @NlsContexts.Label String) {
        val tooltipManager = IdeTooltipManager.getInstance()
        val foreground = tooltipManager.getTextForeground(false)
        val background = tooltipManager.getTextBackground(false)
        val borderColor = JBUI.CurrentTheme.Tooltip.borderColor()
        val label = JLabel(message)
        label.foreground = foreground
        label.background = background
        JBPopupFactory.getInstance()
            .createBalloonBuilder(label)
            .setFillColor(background)
            .setBorderColor(borderColor)
            .createBalloon()
            .show(getBestBalloonPosition(dataContext), Balloon.Position.above)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            systemId == e.getData(ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID) &&
                getModule(e) != null &&
                getUnifiedCoordinates(e) != null
    }

    init {
        templatePresentation.text = PackageSearchBundle.message("packagesearch.actions.showToolWindow.text")
    }
}