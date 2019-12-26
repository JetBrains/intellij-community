package circlet.plugins.pipelines.services

import circlet.plugins.pipelines.utils.*
import circlet.utils.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.openapi.vfs.*
import libraries.coroutines.extra.*
import libraries.klogging.*
import runtime.reactive.*

// listens to file system and exposes script dsl file
@Service
class SpaceKtsFileDetector(val project: Project) : LifetimedDisposable by LifetimedDisposableImpl(), KLogging() {

    val dslFile: Property<VirtualFile?> get() = _dslFile

    private val _dslFile = mutableProperty<VirtualFile?>(null)

    private val fileListener = object : VirtualFileListener {
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

    init {
        // todo: make launch
        refreshScript()
        LocalFileSystem.getInstance().addVirtualFileListener(fileListener)
    }

    fun refreshScript() {
        runReadAction {
            if (!project.isDisposed) {
                _dslFile.value = checkIsDslFileExists()
            }
        }
    }

    fun handleFileChanged(name: String) {
        if (DslFileFinder.checkFileNameIsApplicable(name)) {
            refreshScript()
        }
    }

    private fun checkIsDslFileExists(): VirtualFile? {
        return DslFileFinder.find(project)
    }

}
