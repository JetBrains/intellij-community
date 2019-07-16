package circlet.plugins.pipelines.utils

import com.intellij.openapi.project.*
import com.intellij.openapi.vfs.*

object DslFileFinder {

    fun find(project: Project): VirtualFile? {
        val basePath = project.basePath ?: return null
        val baseDirFile = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return null
        return find(baseDirFile)
    }

    fun checkFileNameIsApplicable(name: String): Boolean {
        return "circlet.kts".equals(name, true)
    }

    private fun find(baseDirFile: VirtualFile) : VirtualFile? {
        return baseDirFile.children.firstOrNull {
            checkFileNameIsApplicable(it.name)
        }
    }
}
