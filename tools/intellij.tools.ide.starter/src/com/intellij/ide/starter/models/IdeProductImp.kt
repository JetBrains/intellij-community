package com.intellij.ide.starter.models

object IdeProductImp : IdeProduct {
  /** GoLand */
  override val GO = IdeInfo(
    productCode = "GO",
    platformPrefix = "GoLand",
    executableFileName = "goland",
    fullName = "GoLand",
    qodanaProductCode = "QDGO"
  )

  /** IntelliJ IDEA */
  override val IU = IdeInfo(
    productCode = "IU",
    platformPrefix = "idea",
    executableFileName = "idea",
    fullName = "IDEA",
    qodanaProductCode = "QDJVM"
  )

  /** IntelliJ Community */
  override val IC = IdeInfo(
    productCode = "IC",
    platformPrefix = "Idea",
    executableFileName = "idea",
    fullName = "IDEA Community",
    qodanaProductCode = "QDJVMC"
  )

  /** Android Studio */
  override val AI = IdeInfo(
    productCode = "AI",
    platformPrefix = "AndroidStudio",
    executableFileName = "studio",
    fullName = "Android Studio"
  )

  /** WebStorm */
  override val WS = IdeInfo(
    productCode = "WS",
    platformPrefix = "WebStorm",
    executableFileName = "webstorm",
    fullName = "WebStorm",
    qodanaProductCode = "QDJS"
  )

  /** PhpStorm */
  override val PS = IdeInfo(
    productCode = "PS",
    platformPrefix = "PhpStorm",
    executableFileName = "phpstorm",
    fullName = "PhpStorm",
    qodanaProductCode = "QDPHP"
  )

  /** DataGrip */
  override val DB = IdeInfo(
    productCode = "DB",
    platformPrefix = "DataGrip",
    executableFileName = "datagrip",
    fullName = "DataGrip"
  )

  /** RubyMine */
  override val RM = IdeInfo(
    productCode = "RM",
    platformPrefix = "Ruby",
    executableFileName = "rubymine",
    fullName = "RubyMine"
  )

  /** PyCharm */
  override val PY = IdeInfo(
    productCode = "PY",
    platformPrefix = "Python",
    executableFileName = "pycharm",
    fullName = "PyCharm",
    qodanaProductCode = "QDPY"
  )

  /** CLion */
  override val CL: IdeInfo = IdeInfo(
    productCode = "CL",
    platformPrefix = "CLion",
    executableFileName = "clion",
    fullName = "CLion",
    qodanaProductCode = "QDCPP"
  )

  /** DataSpell */
  override val DS: IdeInfo = IdeInfo(
    productCode = "DS",
    platformPrefix = "DataSpell",
    executableFileName = "dataspell",
    fullName = "DataSpell"
  )

  /** PyCharm Community */
  override val PC: IdeInfo = IdeInfo(
    productCode = "PC",
    platformPrefix = "PyCharmCore",
    executableFileName = "pycharm",
    fullName = "PyCharm",
    qodanaProductCode = "QDPYC"
  )

  /** Aqua */
  override val QA: IdeInfo = IdeInfo(
    productCode = "QA",
    platformPrefix = "Aqua",
    executableFileName = "aqua",
    fullName = "Aqua"
  )

  /** RustRover */
  override val RR: IdeInfo = IdeInfo(
    productCode = "RR",
    platformPrefix = "RustRover",
    executableFileName = "rustrover",
    fullName = "RustRover"
  )

  /** Rider */
  override val RD = IdeInfo(
    productCode = "RD",
    platformPrefix = "Rider",
    executableFileName = "rider",
    fullName = "Rider"
  )

  /** Gateway */
  override val GW = IdeInfo(
    productCode = "GW",
    platformPrefix = "Gateway",
    executableFileName = "gateway",
    fullName = "Gateway"
  )
}