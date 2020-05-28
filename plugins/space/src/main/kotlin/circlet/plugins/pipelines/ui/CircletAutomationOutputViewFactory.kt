package circlet.plugins.pipelines.ui

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter

class CircletAutomationOutputViewFactory {
    fun create(project: Project): CircletAutomationOutputView {
        val buildLogView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console as ConsoleViewImpl
        val runLogView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console as ConsoleViewImpl

        val splitPanel = Splitter(false)
        splitPanel.firstComponent = buildLogView.component
        splitPanel.secondComponent = runLogView.component
        return CircletAutomationOutputView(buildLogView)
    }
}
