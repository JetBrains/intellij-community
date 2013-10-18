package com.jetbrains.python.formatter;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class PyCodeStylePanel extends CodeStyleAbstractPanel {
  private JPanel myPanel;

  protected PyCodeStylePanel(CodeStyleSettings settings) {
    super(settings);
  }

  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return HighlighterFactory.createHighlighter(new PyHighlighter(LanguageLevel.PYTHON26), scheme);
  }

  @Override
  protected int getRightMargin() {
    return 80;
  }

  @Override
  protected void prepareForReformat(PsiFile psiFile) {
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  @Override
  protected String getPreviewText() {
    return "";
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
  }

  @Override
  public void apply(CodeStyleSettings settings) {
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    return false;
  }

  @Override
  public JComponent getPanel() {
    return myPanel;
  }
}
