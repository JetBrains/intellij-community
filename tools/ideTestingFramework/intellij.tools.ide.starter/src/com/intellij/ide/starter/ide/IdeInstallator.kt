package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.IdeInfo

interface IdeInstallator {
  /**
   * @return <Build Number, InstalledIde>
   */
  fun install(ideInfo: IdeInfo): Pair<String, InstalledIde>
}