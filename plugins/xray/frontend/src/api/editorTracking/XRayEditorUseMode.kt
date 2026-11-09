// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.frontend.api.editorTracking

import com.intellij.openapi.util.Key
import com.intellij.xray.frontend.api.XRayBundle
import com.intellij.xray.frontend.api.XRayMode
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

/**
 * Key pointing to the current line the user is focused on
 */
@ApiStatus.Internal
val XRAY_CURRENT_LINE: Key<Int> = Key.create("xray.current.line")

/**
 * Key pointing to the position in the line the user is focused on
 */
@ApiStatus.Internal
val XRAY_CURRENT_LINE_POSITION: Key<Int> = Key.create("xray.current.line.position")

/**
 * Key pointing to the mode of X-Ray view used by the editor
 */
@ApiStatus.Internal
val XRAY_EDITOR_USE_MODE: Key<XRayEditorUseMode> = Key.create("xray.editor.use.mode")

@ApiStatus.Internal
val XRAY_EDITOR_MODE: Key<XRayMode> = Key.create("xray.editor.mode")

/**
 * Different modes of the X-Ray view in the editor
 */
@ApiStatus.Internal
enum class XRayEditorUseMode(@NonNls private val titleKey: String) {
  READING("xray.config.mode.reading"),
  DEBUGGING("xray.config.mode.debugging"),
  EDITING("xray.config.mode.editing");

  val title: @Nls String get() = XRayBundle.message(titleKey)
}
