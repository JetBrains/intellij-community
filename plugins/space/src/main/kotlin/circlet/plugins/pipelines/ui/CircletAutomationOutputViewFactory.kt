package circlet.plugins.pipelines.ui

import com.intellij.execution.filters.*
import com.intellij.execution.impl.*
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.*
import javax.swing.*

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
