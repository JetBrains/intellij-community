package circlet.plugins.pipelines.utils

import com.intellij.ide.plugins.*
import java.io.*


object JarFinder {
    val pluginFiles: List<File> by lazy {
        val plugins = PluginManager.getPlugins()
        val pluginName = "Circlet Integration"
        val currentPlugin = plugins.firstOrNull { x -> x.name == pluginName } ?: error("Can't find `$pluginName` plugin")
        currentPlugin.path.getFiles()
    }

    fun find(name: String) : File {
        val pluginFiles = pluginFiles
        return pluginFiles.firstOrNull { x -> x.name.contains(name) } ?: error("Can't find jar $name")
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
