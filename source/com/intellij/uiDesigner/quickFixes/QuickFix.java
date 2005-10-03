package com.intellij.uiDesigner.quickFixes;

import com.intellij.uiDesigner.GuiEditor;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class QuickFix {
  public static final QuickFix[] EMPTY_ARRAY = new QuickFix[]{};

  protected final GuiEditor myEditor;
  private final String myName;

  public QuickFix(final GuiEditor editor, final String name){
    if (editor == null) {
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("editor cannot be null");
    }
    if (name == null) {
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("name cannot be null");
    }
    myEditor = editor;
    myName = name;
  }

  /**
   * @return name of the quick fix. Never <code>null</code>.
   */
  public final String getName() {
    return myName;
  }

  public abstract void run();
}
