// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.models

/**
 * Enum representing all supported IDE product types.
 * Used as DI tag for [IdeInfo] bindings.
 *
 * Each IDE-specific module (e.g., `intellij.tools.ide.starter.product.goland`)
 * registers its [IdeInfo] in DI using the corresponding [IdeInfoType] as a tag.
 */
enum class IdeInfoType(
  val productCode: String,
  val executableFileName: String,
  val fullName: String,
) {
  GOLAND("GO", "goland", "GoLand"),
  IDEA_ULTIMATE("IU", "idea", "IDEA"),
  IDEA_COMMUNITY("IC", "idea", "IDEA Community"),
  ANDROID_STUDIO("AI", "studio", "Android Studio"),
  WEBSTORM("WS", "webstorm", "WebStorm"),
  PHPSTORM("PS", "phpstorm", "PhpStorm"),
  DATAGRIP("DB", "datagrip", "DataGrip"),
  RUBYMINE("RM", "rubymine", "RubyMine"),
  PYCHARM("PY", "pycharm", "PyCharm"),
  CLION("CL", "clion", "CLion"),
  PYCHARM_COMMUNITY("PC", "pycharm", "PyCharm"),
  AQUA("QA", "aqua", "Aqua"),
  RUSTROVER("RR", "rustrover", "RustRover"),
  RIDER("RD", "rider", "Rider"),
  GATEWAY("GW", "gateway", "Gateway");

  companion object {
    fun fromProductCode(productCode: String): IdeInfoType =
      entries.find { it.productCode == productCode } ?: error("Unknown product code: $productCode")
  }
}


/** Interface for IDE product initialization discovered via [java.util.ServiceLoader]. */
interface IdeProductInit {
  val ideInfoType: IdeInfoType
  val ideInfo: IdeInfo
}
