package com.intellij.ide.starter.models

object IdeProductImp : IdeProduct {
  /** GoLand */
  override val GO = IdeInfo(
    productCode = "GO",
    platformPrefix = "GoLand",
    executableFileName = "goland",
  )

  /** IntelliJ Ultimate */
  override val IU = IdeInfo(
    productCode = "IU",
    platformPrefix = "idea",
    executableFileName = "idea"
  )

  /** IntelliJ Community */
  override val IC = IdeInfo(
    productCode = "IC",
    platformPrefix = "Idea",
    executableFileName = "idea"
  )

  /** Android Studio */
  override val AI = IdeInfo(
    productCode = "AI",
    platformPrefix = "AndroidStudio",
    executableFileName = "studio"
  )

  /** WebStorm */
  override val WS = IdeInfo(
    productCode = "WS",
    platformPrefix = "WebStorm",
    executableFileName = "webstorm"
  )

  /** PhpStorm */
  override val PS = IdeInfo(
    productCode = "PS",
    platformPrefix = "PhpStorm",
    executableFileName = "phpstorm"
  )

  /** DataGrip */
  override val DB = IdeInfo(
    productCode = "DB",
    platformPrefix = "DataGrip",
    executableFileName = "datagrip"
  )

  /** RubyMine */
  override val RM = IdeInfo(
    productCode = "RM",
    platformPrefix = "Ruby",
    executableFileName = "rubymine"
  )

  /** PyCharm Professional */
  override val PY = IdeInfo(
    productCode = "PY",
    platformPrefix = "Python",
    executableFileName = "pycharm"
  )

  /** CLion */
  override val CL: IdeInfo = IdeInfo(
    productCode = "CL",
    platformPrefix = "CLion",
    executableFileName = "clion"
  )
}