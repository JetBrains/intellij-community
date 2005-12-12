package com.intellij.uiDesigner.quickFixes;

import com.intellij.uiDesigner.designSurface.GuiEditor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class QuickFix {
  public static final QuickFix[] EMPTY_ARRAY = new QuickFix[]{};

  protected final GuiEditor myEditor;
  private final String myName;

  public QuickFix(@NotNull final GuiEditor editor, @NotNull final String name){
    myEditor = editor;
    myName = name;
  }

  /**
   * @return name of the quick fix.
   */
  @NotNull public final String getName() {
    return myName;
  }

  public abstract void run();
}
