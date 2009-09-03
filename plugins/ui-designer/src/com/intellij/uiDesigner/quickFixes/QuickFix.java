package com.intellij.uiDesigner.quickFixes;

import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class QuickFix {
  public static final QuickFix[] EMPTY_ARRAY = new QuickFix[]{};

  protected final GuiEditor myEditor;
  private final String myName;
  protected RadComponent myComponent;

  public QuickFix(@NotNull final GuiEditor editor, @NotNull final String name, @Nullable RadComponent component) {
    myEditor = editor;
    myName = name;
    myComponent = component;
  }

  /**
   * @return name of the quick fix.
   */
  @NotNull public final String getName() {
    return myName;
  }

  public abstract void run();

  public RadComponent getComponent() {
    return myComponent;
  }
}
