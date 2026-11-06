// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.wm.ToolWindowAnchor;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated this is a part of the old implementation of UI Designer, it shouldn't be used anymore (IDEA-395701)
 */
@Deprecated(forRemoval = true)
public abstract class DesignerCustomizations {
  public static final ExtensionPointName<DesignerCustomizations> EP_NAME = ExtensionPointName.create("Designer.customizations");

  /**
   * Default location of the palette
   */
  public @NotNull ToolWindowAnchor getPaletteAnchor() {
    return ToolWindowAnchor.RIGHT;
  }

  /**
   * Default location of the designer/structure window
   */
  public @NotNull ToolWindowAnchor getStructureAnchor() {
    return ToolWindowAnchor.LEFT;
  }
}