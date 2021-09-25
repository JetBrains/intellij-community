// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant
import org.jetbrains.annotations.Nls
import java.util.*
import kotlin.reflect.KCallable

internal class PyTestCustomOption(property: KCallable<*>, vararg supportedTypes: PyRunTargetVariant) {
  val name: String = property.name
  val isBooleanType: Boolean = property.returnType.classifier == Boolean::class

  @field:Nls
  val localizedName: String

  init {
    localizedName = property.annotations.filterIsInstance<ConfigField>().firstOrNull()?.localizedName?.let {
      PyBundle.message(it)
    } ?: name
  }

  /**
   * Types to display this option for
   */
  val mySupportedTypes: EnumSet<PyRunTargetVariant> = EnumSet.copyOf(supportedTypes.asList())
}
