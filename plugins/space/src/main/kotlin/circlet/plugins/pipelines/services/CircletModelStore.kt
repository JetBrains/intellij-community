package circlet.plugins.pipelines.services

import circlet.plugins.pipelines.utils.*
import circlet.plugins.pipelines.viewmodel.*
import circlet.utils.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.openapi.vfs.*
import libraries.klogging.*

@Service
class CircletModelStore(val project: Project): LifetimedComponent by SimpleLifetimedComponent(), KLogging() {
    val viewModel = ScriptWindowViewModel(lifetime, project)
    init {
        val listener = CircletAutomationListener(project)
        listener.listen(viewModel)
        lifetime.add { listener.disposeComponent() }

        fun refreshScript() {
            ApplicationManager.getApplication().runReadAction {
                if (!project.isDisposed) {
                    val dslFileExists = checkIsDslFileExists()
                    logger.debug("refresh. dslFileExists=$dslFileExists")
                    viewModel.script.value = if (dslFileExists) createEmptyScriptViewModel(viewModel.scriptLifetimes.next()) else null
                }
            }
        }

        fun handleFileChanged(name: String) {
            if (DslFileFinder.checkFileNameIsApplicable(name)) {
                refreshScript()
            }
        }

        val fileListener = object: VirtualFileListener {
            override fun propertyChanged(event: VirtualFilePropertyEvent) {
                if (event.propertyName == "name") {
                    handleFileChanged(event.oldValue.toString())
                    handleFileChanged(event.newValue.toString())
                }
            }

            override fun fileCreated(event: VirtualFileEvent) {
                handleFileChanged(event.fileName)
            }

            override fun fileDeleted(event: VirtualFileEvent) {
                handleFileChanged(event.fileName)
            }

            override fun fileMoved(event: VirtualFileMoveEvent) {
                handleFileChanged(event.fileName)
            }
        }

        refreshScript()

        LocalFileSystem.getInstance().addVirtualFileListener(fileListener)
    }

    private fun checkIsDslFileExists() : Boolean {
        return DslFileFinder.find(project) != null
    }
}
