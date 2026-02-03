package com.intellij.ide.starter.ide.installer

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IdeDistributionFactory
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.FileSystem.deleteRecursivelyQuietly
import com.intellij.openapi.util.SystemInfo
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolute
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.time.Duration.Companion.minutes

/**
 * Use an existing installed IDE instead of downloading one.
 * Set it as:
 * `bindSingleton<IdeInstallerFactory>(overrides = true) {
 *    object : IdeInstallerFactory() {
 *       override fun createInstaller(ideInfo: IdeInfo, downloader: IdeDownloader): IdeInstaller =
 *         ExistingIdeInstaller(Paths.get(pathToInstalledIDE))
 *     }
 * }`
 */
@Suppress("unused")
class ExistingIdeInstaller(private val installedIdePath: Path) : IdeInstaller {
  override suspend fun install(ideInfo: IdeInfo): Pair<String, InstalledIde> {
    val ideInstaller = IdeInstallerFile(installedIdePath, "locally-installed-ide")
    val installDir = GlobalPaths.instance.getLocalCacheDirectoryFor("builds").resolve("${ideInfo.productCode}-${ideInstaller.buildNumber}")
    @OptIn(ExperimentalPathApi::class)
    installDir.deleteRecursivelyQuietly()
    val destDir = installDir.resolve(installedIdePath.name)
    if (SystemInfo.isMac) {
      val taskName = "copy app"
      ProcessExecutor(taskName, null, 5.minutes, emptyMap(),
                      stderrRedirect = ExecOutputRedirect.ToStdOut(taskName), stdoutRedirect = ExecOutputRedirect.ToStdOut(taskName),
                      args = listOf("ditto", installedIdePath.absolute().toString(), destDir.absolute().toString())).start()
    }
    else {
      @OptIn(ExperimentalPathApi::class)
      installedIdePath.copyToRecursively(destDir.createDirectories(), followLinks = false, overwrite = true)
    }
    return Pair(
      ideInstaller.buildNumber,
      di.direct.instance<IdeDistributionFactory>().installIDE(installDir.toFile(), ideInfo.executableFileName)
    )
  }
}
