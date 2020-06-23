package circlet.plugins.pipelines.utils

import com.intellij.ide.plugins.*
import com.intellij.openapi.extensions.*
import java.io.*


object JarFinder {
    private val spacePluginFiles: List<File> by lazy {
        getPluginFiles("com.jetbrains.space")
    }

    private val kotlinPluginFiles: List<File> by lazy {
        getPluginFiles("org.jetbrains.kotlin")
    }

    fun find(name: String) : File {
        return findFile(name, spacePluginFiles)
    }

    fun findInKotlinPlugin(name: String) : File {
        return findFile(name, kotlinPluginFiles)
    }

    private fun findFile(name: String, files: List<File>) : File {
        return files.firstOrNull { x -> x.name.contains(name) } ?: error("Can't find jar $name")

    }

    private fun getPluginFiles(pluginId: String) : List<File> {
        val currentPlugin =  PluginManager.getPlugin(PluginId.findId(pluginId))?: error("Can't find `$pluginId` plugin")
        return currentPlugin.path.getFiles()
    }

    private fun File.getFiles() : List<File> {
        val files = this.listFiles()
        val res = mutableListOf<File>()
        files?.forEach {file ->
            if (file.isDirectory) {
                res.addAll(file.getFiles())
            }
            else {
                res.add(file)
            }
        }
        return res
    }
}
