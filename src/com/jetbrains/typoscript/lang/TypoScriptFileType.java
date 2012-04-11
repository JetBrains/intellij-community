package com.jetbrains.typoscript.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author lene
 *         Date: 03.04.12
 */
public class TypoScriptFileType extends LanguageFileType {
  private static final Icon ICON = IconLoader.getIcon("/icons/typo3.png");   //todo[lene]

  public static final TypoScriptFileType INSTANCE = new TypoScriptFileType();

  private TypoScriptFileType() {
    super(TypoScriptLanguage.INSTANCE);
   /* FileTypeEditorHighlighterProviders.INSTANCE.addExplicitExtension(this, new EditorHighlighterProvider() {//todo[lene]
      @Override
      public EditorHighlighter getEditorHighlighter(@Nullable Project project,
                                                    @NotNull FileType fileType, @Nullable VirtualFile virtualFile,
                                                    @NotNull EditorColorsScheme colors) {
        return new TypoScriptEditorHighlighter(project, virtualFile, colors);
      }
    });*/
  }

  @NotNull
  @Override
  public String getName() {
    return "TypoScript";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "TypoScript";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "ts";
  }

  @Override
  public Icon getIcon() {
    return ICON;//todo[lene]
  }
}


