// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.EnumSet
import kotlin.reflect.KCallable

/**
 * @param helpKey the bundle key of the text for the context help icon of the option, or null if the option has no help
 */
internal class PyTestCustomOption @JvmOverloads constructor(
  property: KCallable<*>,
  vararg supportedTypes: PyRunTargetVariant,
  @PropertyKey(resourceBundle = PyBundle.BUNDLE) helpKey: String? = null,
) {
  val name: String = property.name
  val isBooleanType: Boolean = property.returnType.classifier == Boolean::class

  @field:Nls
  val localizedName: String

  init {
    localizedName = property.annotations.filterIsInstance<ConfigField>().firstOrNull()?.localizedName?.let {
      PyBundle.message(it)
    } ?: name
  }

  @field:Nls
  val localizedHelp: String? = if (helpKey != null) PyBundle.message(helpKey) else null

  /**
   * Types to display this option for
   */
  val mySupportedTypes: EnumSet<PyRunTargetVariant> = EnumSet.copyOf(supportedTypes.asList())
}
