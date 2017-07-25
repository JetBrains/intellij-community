package training.lang

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId

/**
 * @author Sergey Karashevich
 */
class MockLangSupport(override val FILE_EXTENSION: String) : LangSupport {
    override fun checkSdkCompatibility(sdk: Sdk, sdkTypeId: SdkTypeId) {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun applyToProjectAfterConfigure(): (Project) -> Unit {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun acceptLang(ext: String): Boolean {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun applyProjectSdk(project: Project) {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getModuleBuilder(): ModuleBuilder {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}
