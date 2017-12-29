import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.ZipUtil
import java.io.File
import java.io.FileFilter

/**
 * Deploys Educational plugin into config/plugins directory.
 * Plugins directory is specified in "idea.plugins.path" system property.
 */
fun main(args: Array<String>) {
  var rootPath = File("").absolutePath
  val outputDir = File(rootPath, FileUtil.toSystemDependentName(System.getProperty("idea.plugins.path")))
  val communityPath = FileUtil.join(rootPath, "community")
  if (File(communityPath).exists()) {
    rootPath = communityPath
  }
  val resourcesPath = FileUtil.join(rootPath, "python", "educational-python", "resources")
  val files = File(resourcesPath).listFiles(
    FileFilter { pathname -> pathname.name.matches(Regex("EduTools-[0-9.]+-[0-9.]+-[0-9.]+.zip")) })
  if (files.isEmpty()) {
    throw IllegalStateException("EduTools bundled plugin is not found in $resourcesPath")
  }
  ZipUtil.extract(files[0], outputDir, null)
}
