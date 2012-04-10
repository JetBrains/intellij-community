package com.jetbrains.typoscript.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author lene
 *         Date: 03.04.12
 */
public class TypoScriptFileType  extends LanguageFileType {
  public static final TypoScriptFileType INSTANCE = new TypoScriptFileType();

  private TypoScriptFileType() {
    super(TypoScriptLanguage.INSTANCE);
 /*   FileTypeEditorHighlighterProviders.INSTANCE.addExplicitExtension(this, new EditorHighlighterProvider() {//todo[lene]
      @Override
      public EditorHighlighter getEditorHighlighter(@Nullable Project project,
                                                    @NotNull FileType fileType, @Nullable VirtualFile virtualFile,
                                                    @NotNull EditorColorsScheme colors) {
        return new PlayEditorHighlighter(project, virtualFile, colors);
      }
    });*/
  }

  @NotNull
  public String getName() {
    return "TypoScript";
  }

  @NotNull
  public String getDescription() {
    return "TypoScript";
  }

  @NotNull
  public String getDefaultExtension() {
    return "ts"; //todo[lene]
  }

  public Icon getIcon() {
    return null;//todo[lene]
  }
}


