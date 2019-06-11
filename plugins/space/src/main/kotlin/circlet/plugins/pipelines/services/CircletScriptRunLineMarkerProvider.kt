package circlet.plugins.pipelines.services

import com.intellij.execution.*
import com.intellij.execution.lineMarker.*
import com.intellij.icons.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ui.*
import com.intellij.psi.*
import com.intellij.ui.*
import org.jetbrains.kotlin.idea.refactoring.fqName.*
import org.jetbrains.kotlin.idea.references.*

class CircletScriptRunLineMarkerProvider : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {

        val refs = element.references
        refs.forEach {
            if (it is KtSimpleNameReference) {
                val r = it.resolve()
                if (r != null) {
                    val fqnName = r.getKotlinFqName()
                    if (fqnName != null) {
                        if (fqnName.asString() == "circlet.pipelines.config.dsl.api.Project.task") {
                            val runAction = object : DumbAwareActionButton(ExecutionBundle.message("run.configurable.display.name"), AllIcons.RunConfigurations.TestState.Run) {
                                override fun actionPerformed(e: AnActionEvent) {
                                    Messages.showInfoMessage("run build", "circlet")
                                }
                            }
                            return Info(runAction)
                        }
                    }
                }
            }
        }

        return null
    }
}
