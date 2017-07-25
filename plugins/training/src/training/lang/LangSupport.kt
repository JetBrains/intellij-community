package training.lang

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId

/**
 * @author Sergey Karashevich
 */
interface LangSupport {

    val FILE_EXTENSION: String

    companion object {
        val EP_NAME = "training.TrainingLangExtension"
    }

    fun acceptLang(ext: String): Boolean
    fun applyProjectSdk(project: Project): Unit
    fun applyToProjectAfterConfigure(): (Project) -> Unit
    fun getModuleBuilder(): ModuleBuilder

    fun checkSdkCompatibility(sdk: Sdk, sdkTypeId: SdkTypeId)
}