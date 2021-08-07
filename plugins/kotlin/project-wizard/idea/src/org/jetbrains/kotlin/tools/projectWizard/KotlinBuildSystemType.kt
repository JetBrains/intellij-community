// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.wizard.BuildSystemType
import com.intellij.openapi.extensions.ExtensionPointName

abstract class KotlinBuildSystemType<P>(override val name: String) : BuildSystemType<KotlinSettings, P> {
  companion object{
    var EP_NAME = ExtensionPointName<KotlinBuildSystemType<*>>("com.intellij.newProjectWizard.buildSystem.kotlin")
  }
}