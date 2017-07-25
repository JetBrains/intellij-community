package training.util

import com.intellij.openapi.components.ServiceManager
import java.io.File
import java.io.FileFilter

abstract class UrlProvider {
    abstract val statsServerPostUrl: String
    
    abstract val experimentDataUrl: String
    
    companion object {
        fun getInstance(): UrlProvider = ServiceManager.getService(UrlProvider::class.java)
    }

}

abstract class FilePathProvider {
    abstract fun getUniqueFile(): File
    abstract fun getDataFiles(): List<File>
    abstract fun getStatsDataDirectory(): File
    
    companion object {
        fun getInstance(): FilePathProvider = ServiceManager.getService(FilePathProvider::class.java)
    }

}

class InternalUrlProvider: UrlProvider() {
    private val localhost = "http://localhost"
    private val internalHost = "http://unit-617.labs.intellij.net"

    private val host: String
        get() = if (isPropertyExists("stats.collector.localhost.server")) localhost else internalHost
    
    
    override val statsServerPostUrl = "http://test.jetstat-resty.aws.intellij.net/uploadstats"
    override val experimentDataUrl = "$host:8090/experiment/info"
}



open class UniqueFilesProvider(private val baseName: String, private val rootDirectoryComputer: () -> File) : FilePathProvider() {
    
    constructor(baseName: String, rootDir: File) : this(baseName, { rootDir })
    
    override fun getUniqueFile(): File {
        val dir = getStatsDataDirectory()

        val currentMaxIndex = dir
                .listFiles(FileFilter { it.isFile })
                .filter { it.name.startsWith(baseName) }
                .map { it.name.substringAfter('_') }
                .filter { it.isIntConvertable() }
                .map { it.toInt() }
                .max()
        
        val newIndex = if (currentMaxIndex != null) currentMaxIndex + 1 else 0
        
        val file = File(dir, "${baseName}_$newIndex")
        return file
    }

    override fun getDataFiles(): List<File> {
        val dir = getStatsDataDirectory()
        return dir.listFiles(FileFilter { it.isFile })
                .filter { it.name.startsWith(baseName) }
                .filter { it.name.substringAfter('_').isIntConvertable() }
                .sortedBy { it.getChunkNumber() }
    }

    override fun getStatsDataDirectory(): File {
        val dir = File(rootDirectoryComputer(), "completion-stats-data")
        if (!dir.exists()) {
            dir.mkdir()
        }
        return dir
    }
    
    private fun File.getChunkNumber() = this.name.substringAfter('_').toInt()

    private fun String.isIntConvertable(): Boolean {
        try {
            this.toInt()
            return true
        } catch (e: NumberFormatException) {
            return false
        }
    }

}

fun isPropertyExists(name: String) = System.getProperty(name) != null