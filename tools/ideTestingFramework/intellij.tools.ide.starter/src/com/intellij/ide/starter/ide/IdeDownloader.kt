package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.IdeInfo
import java.nio.file.Path

interface IdeDownloader {
  fun downloadIdeInstaller(ideInfo: IdeInfo, installerDirectory: Path): IdeInstaller
}