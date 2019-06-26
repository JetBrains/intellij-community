package circlet.plugins.pipelines.utils

import com.intellij.openapi.vfs.*

object DslFileFinder {
    fun find(baseDirFile: VirtualFile) : VirtualFile? {
        return baseDirFile.children.firstOrNull {
            checkFileNameIsApplicable(it.name)
        }
    }

    fun checkFileNameIsApplicable(name: String): Boolean {
        return "circlet.kts".equals(name, true)
    }
}
