// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
}