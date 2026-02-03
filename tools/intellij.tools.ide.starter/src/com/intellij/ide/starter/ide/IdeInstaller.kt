package com.intellij.ide.starter.ide

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import org.kodein.di.direct
import org.kodein.di.instance

interface IdeInstaller {
  val downloader: IdeDownloader
    get() = di.direct.instance<IdeDownloader>()

  /**
   * @return <Build Number, InstalledIde>
   */
  suspend fun install(ideInfo: IdeInfo): Pair<String, InstalledIde>
}