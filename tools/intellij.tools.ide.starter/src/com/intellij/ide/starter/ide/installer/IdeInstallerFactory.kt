package com.intellij.ide.starter.ide.installer

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.useInstaller
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IdeDownloader
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import org.kodein.di.direct
import org.kodein.di.instance

open class IdeInstallerFactory {
  open fun createInstaller(ideInfo: IdeInfo, downloader: IdeDownloader = di.direct.instance<IdeDownloader>()): IdeInstaller {
    if (!ConfigurationStorage.useInstaller()) {
      return IdeFromCodeInstaller()
    }

    return if (ideInfo.productCode == IdeInfoType.ANDROID_STUDIO.productCode)
      AndroidInstaller()
    else
      StandardInstaller(downloader)
  }
}