import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.venvReader.VirtualEnvReader
import org.junit.Assert
import org.junit.Test
import kotlin.io.path.absolutePathString

class PySdkSettingsTest {
  @Test
  fun testPreferredVirtualEnvBasePath() {
    val settings = PySdkSettings()

    val defaultVenvDir = VirtualEnvReader.Instance.getVEnvRootDir().absolutePathString()
    var preferredPath = settings.getPreferredVirtualEnvBasePath(null)
    Assert.assertEquals(defaultVenvDir, preferredPath)

    val projectPath = "/path/to/project"
    val projectVenv = "$projectPath/${VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME}"
    preferredPath = settings.getPreferredVirtualEnvBasePath(projectPath)
    Assert.assertEquals(projectVenv, preferredPath)

    settings.onVirtualEnvCreated("/path/to/sdk", projectVenv, projectPath)
    preferredPath = settings.getPreferredVirtualEnvBasePath(projectPath)
    Assert.assertEquals(projectVenv, preferredPath)

    // created venv outside of project
    settings.onVirtualEnvCreated("/path/to/sdk", "$defaultVenvDir/.venv", projectPath)
    preferredPath = settings.getPreferredVirtualEnvBasePath(projectPath)
    Assert.assertEquals("$defaultVenvDir/project", preferredPath)
  }
}