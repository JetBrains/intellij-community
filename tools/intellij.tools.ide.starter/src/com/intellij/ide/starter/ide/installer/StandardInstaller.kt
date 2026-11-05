package com.intellij.ide.starter.ide.installer

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IdeArchiveExtractor
import com.intellij.ide.starter.ide.IdeDistributionFactory
import com.intellij.ide.starter.ide.IdeDownloader
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.FileSystem.deleteRecursivelyQuietly
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.OS
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div

/**
 * Prebuilt installer, distributed via archive (.tar.gz, .exe, .dmg).
 * [installerOs] selects the archive. A docker run needs the Linux one on a host of any OS.
 */
class StandardInstaller(
  override val downloader: IdeDownloader = di.direct.instance<IdeDownloader>(),
  val customInstallersDownloadDirectory: Path? = null,
  val installerOs: OS = OS.CURRENT,
) : IdeInstaller {

  override fun install(ideInfo: IdeInfo): Pair<String, InstalledIde> = installForOs(ideInfo.copy(installerOs = installerOs))

  private fun installForOs(ideInfo: IdeInfo): Pair<String, InstalledIde> {
    val installersDirectory = (GlobalPaths.instance.installersDirectory / ideInfo.productCode).createDirectories()
    val installersDownloadDirectory = customInstallersDownloadDirectory ?: installersDirectory

    //Download
    val ideInstaller = downloader.downloadIdeInstaller(ideInfo, installersDownloadDirectory)
    val installDir = GlobalPaths.instance.getLocalCacheDirectoryFor("builds") /
                     "${ideInfo.productCode}-${ideInstaller.buildNumber}-${ideInfo.installerOs.name.lowercase()}"

    if (ideInstaller.buildNumber == "SNAPSHOT") {
      logOutput("Cleaning up SNAPSHOT IDE installation $installDir")
      installDir.deleteRecursivelyQuietly()
    }

    //Unpack
    IdeArchiveExtractor.unpackIdeIfNeeded(ideInstaller.installerFile, installDir)

    //Install
    return Pair(ideInstaller.buildNumber, di.direct.instance<IdeDistributionFactory>().installIDE(installDir, ideInfo.executableFileName))
  }
}