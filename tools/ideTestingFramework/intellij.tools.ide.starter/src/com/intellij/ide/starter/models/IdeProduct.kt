package com.intellij.ide.starter.models

interface IdeProduct {
  /** GoLand */
  val GO: IdeInfo

  /** IntelliJ Ultimate */
  val IU: IdeInfo

  /** IntelliJ Community */
  val IC: IdeInfo

  /** Android Studio */
  val AI: IdeInfo

  /** WebStorm */
  val WS: IdeInfo

  /** PhpStorm */
  val PS: IdeInfo

  /** DataGrip */
  val DB: IdeInfo

  /** RubyMine */
  val RM: IdeInfo

  /** PyCharm Professional */
  val PY: IdeInfo

  /** CLion */
  val CL: IdeInfo
}