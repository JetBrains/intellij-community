package com.intellij.laf.win10;

import com.intellij.ide.ui.LafProvider;
import com.intellij.ide.ui.laf.PluggableLafInfo;
import com.intellij.ide.ui.laf.SearchTextAreaPainter;
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;

public class WinLafProvider implements LafProvider {
  public static final String LAF_NAME = "Windows 10 Light";

  @NotNull
  @Override
  public PluggableLafInfo getLookAndFeelInfo() {
    return new Win10LookAndFeelInfo();
  }

  private static class Win10LookAndFeelInfo extends PluggableLafInfo {
    private Win10LookAndFeelInfo() {
      super(LAF_NAME, WinIntelliJLaf.class.getName());
    }

    @Override
    public SearchTextAreaPainter createSearchAreaPainter(@NotNull SearchAreaContext context) {
      return new Win10SearchPainter(context);
    }

    @Override
    public DarculaEditorTextFieldBorder createEditorTextFieldBorder(EditorTextField editorTextField, EditorEx editor) {
      return new WinIntelliJEditorTextFieldBorder(editorTextField, editor);
    }
  }
}
