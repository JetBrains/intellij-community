// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class ErrorInfo {
  public static final ErrorInfo[] EMPTY_ARRAY = new ErrorInfo[0];

  private final RadComponent myComponent;
  public final @NotNull @Nls String myDescription;
  private final String myPropertyName;
  public final QuickFix[] myFixes;
  private final HighlightDisplayLevel myHighlightDisplayLevel;
  private String myInspectionId;

  public ErrorInfo(IComponent component, final @NonNls String propertyName, final @NotNull @Nls String description,
                   @NotNull HighlightDisplayLevel highlightDisplayLevel, final QuickFix @NotNull [] fixes) {
    myComponent = component instanceof RadComponent ? (RadComponent) component : null;
    myHighlightDisplayLevel = highlightDisplayLevel;
    myPropertyName = propertyName;
    myDescription = description;
    myFixes = fixes;
  }

  public String getPropertyName() {
    return myPropertyName;
  }

  public HighlightDisplayLevel getHighlightDisplayLevel() {
    return myHighlightDisplayLevel;
  }

  public String getInspectionId() {
    return myInspectionId;
  }

  public void setInspectionId(final String inspectionId) {
    myInspectionId = inspectionId;
  }

  public RadComponent getComponent() {
    return myComponent;
  }
}
