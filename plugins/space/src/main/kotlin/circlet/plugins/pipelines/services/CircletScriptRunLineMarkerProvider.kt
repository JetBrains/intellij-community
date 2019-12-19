package circlet.plugins.pipelines.services

import circlet.plugins.pipelines.services.run.*
import com.intellij.execution.*
import com.intellij.execution.lineMarker.*
import com.intellij.icons.*
import com.intellij.openapi.actionSystem.*
import com.intellij.psi.*
import org.jetbrains.kotlin.idea.refactoring.fqName.*
import org.jetbrains.kotlin.idea.references.*
import org.jetbrains.kotlin.psi.*

class CircletScriptRunLineMarkerProvider : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {

        val refs = element.references
        refs.forEach {
            if (it is KtSimpleNameReference) {
                val resolveResult = it.resolve()
                if (resolveResult != null) {
                    val fqnName = resolveResult.getKotlinFqName()
                    if (fqnName != null) {
                        if (fqnName.asString() == "circlet.pipelines.config.dsl.api.Project.job") {
                            val valueArgumentList = element.nextSibling as KtValueArgumentList
                            val taskName = valueArgumentList.arguments.firstOrNull()?.children?.firstOrNull()?.reference?.canonicalText
                            if (taskName != null) {
                                val runAction = object : AnAction(ExecutionBundle.message("run.configurable.display.name"), null, AllIcons.RunConfigurations.TestState.Run) {
                                    override fun actionPerformed(e: AnActionEvent) {
                                        CircletRunConfigurationUtils.run(taskName, element.project)
                                    }
                                }
                                return Info(runAction)
                            }
                        }
                    }
                }
            }
        }

        return null
    }
}
