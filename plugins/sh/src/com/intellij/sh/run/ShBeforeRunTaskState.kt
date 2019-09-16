// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.Attribute

class ShBeforeRunTaskState : BaseState() {
  @get:Attribute("RUN_CONFIG_TYPE")
  var runConfigType by string()
  @get:Attribute("RUN_CONFIG_NAME")
  var runConfigName by string()
}