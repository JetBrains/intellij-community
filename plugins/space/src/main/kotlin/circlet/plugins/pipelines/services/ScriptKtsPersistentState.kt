package circlet.plugins.pipelines.services

import circlet.pipelines.config.*
import circlet.pipelines.config.api.*
import com.intellij.openapi.project.*
import com.intellij.util.io.*
import libraries.io.channels.*
import libraries.klogging.*
import java.io.*
import java.nio.file.*
import java.nio.file.attribute.*

private val log = logger<ScriptKtsPersistentState>()

class ScriptKtsPersistentState(val project: Project) {

    // not for load failure
    fun load(): ScriptConfig? {

        val path = getCacheFile(project)

        val fileAttributes = try {
            Files.readAttributes(path, BasicFileAttributes::class.java)
        } catch (ignored: FileSystemException) {
            return null
        }

        if (fileAttributes == null || !fileAttributes.isRegularFile) {
            return null
        }

        val channel = try {
            Files.newByteChannel(path, setOf(StandardOpenOption.READ))
        } catch (e: NoSuchFileException) {
            return null
        } catch (e: IOException) {
            log.error(e)
            return null
        }

        channel.use {
            return channel.readUTF8Line().parseProjectConfig()
        }
    }

    private fun getCacheFile(project: Project): Path {
        return project.getProjectCachePath("space_automation").resolve(".space.kts.dat")
    }

    fun save(config: ScriptConfig) {
        val path = getCacheFile(project)
        path.safeOutputStream().use {
            it.write(config.printJson().toByteArray())
        }
    }

}
