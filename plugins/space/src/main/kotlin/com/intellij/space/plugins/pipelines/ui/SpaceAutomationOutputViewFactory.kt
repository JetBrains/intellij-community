package com.intellij.space.plugins.pipelines.ui

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter

class SpaceAutomationOutputViewFactory {
  fun create(project: Project): SpaceAutomationOutputView {
    val buildLogView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console as ConsoleViewImpl
    val runLogView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console as ConsoleViewImpl

    val splitPanel = Splitter(false)
    splitPanel.firstComponent = buildLogView.component
    splitPanel.secondComponent = runLogView.component
    return SpaceAutomationOutputView(buildLogView)
  }
}
