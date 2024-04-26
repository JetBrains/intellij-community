// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.quickFixes;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class QuickFix {
  public static final QuickFix[] EMPTY_ARRAY = {};

  protected final GuiEditor myEditor;
  private final @IntentionName String myName;
  protected RadComponent myComponent;

  public QuickFix(@NotNull GuiEditor editor, @NotNull @IntentionName String name, @Nullable RadComponent component) {
    myEditor = editor;
    myName = name;
    myComponent = component;
  }

  /**
   * @return name of the quick fix.
   */
  public final @NotNull
  @IntentionName String getName() {
    return myName;
  }

  public abstract void run();

  public RadComponent getComponent() {
    return myComponent;
  }
}
