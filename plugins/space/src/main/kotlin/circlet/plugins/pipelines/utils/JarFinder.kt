package circlet.plugins.pipelines.utils

import com.intellij.ide.plugins.*
import platform.common.*
import java.io.*


object JarFinder {
    private val spacePluginFiles: List<File> by lazy {
        getPluginFiles("$ProductName Integration")
    }

    private val kotlinPluginFiles: List<File> by lazy {
        getPluginFiles("Kotlin")
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

    private fun getPluginFiles(pluginName: String) : List<File> {
        val plugins = PluginManager.getPlugins()
        val currentPlugin = plugins.firstOrNull { x -> x.name == pluginName } ?: error("Can't find `$pluginName` plugin")
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
