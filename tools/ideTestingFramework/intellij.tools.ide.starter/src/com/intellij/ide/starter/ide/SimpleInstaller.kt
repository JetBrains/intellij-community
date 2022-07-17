package com.intellij.ide.starter.ide

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.path.GlobalPaths
import org.kodein.di.direct
import org.kodein.di.instance
import kotlin.io.path.createDirectories
import kotlin.io.path.div

class SimpleInstaller : IdeInstallator {

  override fun install(ideInfo: IdeInfo): Pair<String, InstalledIde> {
    val installersDirectory = (di.direct.instance<GlobalPaths>().installersDirectory / ideInfo.productCode).createDirectories()
    //Download
    val ideInstaller = di.direct.instance<IdeDownloader>().downloadIdeInstaller(ideInfo, installersDirectory)
    val installDir = di.direct.instance<GlobalPaths>().getCacheDirectoryFor("builds") / "${ideInfo.productCode}-${ideInstaller.buildNumber}"
    //Unpack
    IdeArchiveExtractor.unpackIdeIfNeeded(ideInstaller.installerFile.toFile(), installDir.toFile())
    //Install
    return Pair(ideInstaller.buildNumber, IdeDistributionFactory.installIDE(installDir.toFile(), ideInfo.executableFileName))
  }
}