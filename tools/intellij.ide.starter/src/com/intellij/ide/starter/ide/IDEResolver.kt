package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.IdeInfo
import java.nio.file.Path

interface IDEResolver {

  fun resolveIDE(ideInfo:IdeInfo, installerPath: Path): IdeInstaller

}