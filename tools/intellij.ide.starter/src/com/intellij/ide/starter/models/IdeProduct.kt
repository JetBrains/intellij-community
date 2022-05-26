package com.intellij.ide.starter.models

import com.intellij.ide.starter.utils.Git

enum class IdeProduct(val ideInfo: IdeInfo) {
  /** GoLand */
  GO(IdeInfo.new(
    productCode = "GO",
    platformPrefix = "GoLand",
    executableFileName = "goland",
    jetBrainsCIBuildType = "ijplatform_${Git.branch}_Go_InstallersBuild"
  )),

  /** IntelliJ Ultimate */
  IU(IdeInfo.new(
    productCode = "IU",
    platformPrefix = "idea",
    executableFileName = "idea",
    jetBrainsCIBuildType = "ijplatform_${Git.branch}_IdeaInstallersBuild"
  )),

  /** IntelliJ Community */
  IC(IdeInfo.new(
    productCode = "IC",
    platformPrefix = "Idea",
    executableFileName = "idea",
    jetBrainsCIBuildType = "ijplatform_${Git.branch}_IdeaCommunityInstallersBuild"
  )),

  /** Android Studio */
  AI(IdeInfo.new(
    productCode = "AI",
    platformPrefix = "AndroidStudio",
    executableFileName = "studio",
    jetBrainsCIBuildType = ""
  )),

  /** WebStorm */
  WS(IdeInfo.new(
    productCode = "WS",
    platformPrefix = "WebStorm",
    executableFileName = "webstorm",
    jetBrainsCIBuildType = "ijplatform_${Git.branch}_WebStorm_InstallersBuild"
  )),

  /** PhpStorm */
  PS(IdeInfo.new(
    productCode = "PS",
    platformPrefix = "PhpStorm",
    executableFileName = "phpstorm",
    jetBrainsCIBuildType = "ijplatform_${Git.branch}_PhpStorm_InstallersBuild"
  )),

  /** DataGrip */
  DB(IdeInfo.new(
    productCode = "DB",
    platformPrefix = "DataGrip",
    executableFileName = "datagrip",
    jetBrainsCIBuildType = "ijplatform_${Git.branch}_DG_InstallersBuild"
  )),

  /** RubyMine */
  RM(IdeInfo.new(
    productCode = "RM",
    platformPrefix = "Ruby",
    executableFileName = "rubymine",
    jetBrainsCIBuildType = "ijplatform_${Git.branch}_Ruby_InstallersBuild"
  )),

  /** PyCharm Professional */
  PY(IdeInfo.new(
    productCode = "PY",
    platformPrefix = "Python",
    executableFileName = "pycharm",
    jetBrainsCIBuildType = "ijplatform_${Git.branch}_PyCharm_InstallersBuild"
  ));
}