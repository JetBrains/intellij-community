package com.intellij.ide.starter.ide.installer

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.useInstaller
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IdeDownloader
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.IdeInfo
import org.kodein.di.direct
import org.kodein.di.instance

open class IdeInstallerFactory {
  open fun createInstaller(ideInfo: IdeInfo, downloader: IdeDownloader = di.direct.instance<IdeDownloader>()): IdeInstaller {
    if (!ConfigurationStorage.useInstaller()) {
      return IdeFromCodeInstaller()
    }

    return if (ideInfo.productCode == IdeProductProvider.AI.productCode)
      AndroidInstaller()
    else
      StandardInstaller(downloader)
  }
}