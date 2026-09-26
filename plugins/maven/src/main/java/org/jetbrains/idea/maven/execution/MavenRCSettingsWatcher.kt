// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.swing.JCheckBox

@Deprecated("Old internal Maven statistics collector no more needed")
@ApiStatus.Internal
interface MavenRCSettingsWatcher: Disposable {
  fun registerComponent(settingId: String, component: Component)
  fun registerUseProjectSettingsCheckbox(component: JCheckBox)
}