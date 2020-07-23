package circlet.plugins.pipelines.services

import circlet.pipelines.config.api.ScriptConfig
import circlet.pipelines.config.api.parseProjectConfig
import circlet.pipelines.config.api.printJson
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.util.io.safeOutputStream
import libraries.io.channels.readUTF8Line
import libraries.klogging.logger
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

private val log = logger<ScriptKtsPersistentState>()

class ScriptKtsPersistentState(val project: Project) {

  // not for load failure
  fun load(): ScriptConfig? {

    val path = getCacheFile(project)

    val fileAttributes = try {
      Files.readAttributes(path, BasicFileAttributes::class.java)
    }
    catch (ignored: FileSystemException) {
      return null
    }

    if (fileAttributes == null || !fileAttributes.isRegularFile) {
      return null
    }

    val channel = try {
      Files.newByteChannel(path, setOf(StandardOpenOption.READ))
    }
    catch (e: NoSuchFileException) {
      return null
    }
    catch (e: IOException) {
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
