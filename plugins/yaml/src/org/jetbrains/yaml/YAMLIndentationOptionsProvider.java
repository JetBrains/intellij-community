package org.jetbrains.yaml;

import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.FileTypeIndentOptionsProvider;

/**
 * @author oleg
 */
public class YAMLIndentationOptionsProvider implements FileTypeIndentOptionsProvider {
  public CodeStyleSettings.IndentOptions createIndentOptions() {
    final CodeStyleSettings.IndentOptions indentOptions = new CodeStyleSettings.IndentOptions();
    indentOptions.INDENT_SIZE = 2;
    return indentOptions;
  }

  public FileType getFileType() {
    return YAMLFileType.YML;
  }

  public IndentOptionsEditor createOptionsEditor() {
    return new IndentOptionsEditor();
  }

  public String getPreviewText() {
    return "product: \n" + "  name: RubyMine\n" + "  version: 1.5\n" + "  vendor: JetBrains\n" + "  url: \"http://jetbrains.com/ruby\"";
  }

  public void prepareForReformat(final PsiFile psiFile) {
  }
}
