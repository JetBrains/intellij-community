package com.intellij.uiDesigner;

import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ErrorInfo {
  public static final ErrorInfo[] EMPTY_ARRAY = new ErrorInfo[0];

  private final RadComponent myComponent;
  public final String myDescription;
  private final String myPropertyName;
  public final QuickFix[] myFixes;
  private final HighlightDisplayLevel myHighlightDisplayLevel;
  private String myInspectionId;

  public ErrorInfo(IComponent component, @NonNls final String propertyName, @NotNull final String description,
                   @NotNull HighlightDisplayLevel highlightDisplayLevel, @NotNull final QuickFix[] fixes) {
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
