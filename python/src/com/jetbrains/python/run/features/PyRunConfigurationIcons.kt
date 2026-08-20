// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.features

import com.intellij.execution.configurations.RunConfiguration
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.ui.badgeIcon
import javax.swing.Icon

/**
 * [icon] as it should be shown for [configuration]: badged with the tool the configuration is run through, so that the
 * icon says the same thing as the label next to it. Returns [icon] unchanged when nothing runs the configuration but
 * the interpreter.
 */
internal fun decorateIcon(configuration: RunConfiguration, icon: Icon): Icon {
  val runTool = (configuration as? AbstractPythonRunConfiguration<*>)?.activeRunToolData() ?: return icon
  return badgeIcon(icon, runTool.icon)
}
