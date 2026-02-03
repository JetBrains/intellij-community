// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LayerDescriptor;
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Enables python highlighting for Rest files
 */
public final class RestEditorHighlighter extends LayeredLexerEditorHighlighter {

  public RestEditorHighlighter(@NotNull EditorColorsScheme scheme, @Nullable Project project, @Nullable VirtualFile file) {
    super(SyntaxHighlighterFactory.getSyntaxHighlighter(RestLanguage.INSTANCE, project, file), scheme);

    if (project == null || file == null) return;

    FileType pythonFileType = FileTypeManager.getInstance().findFileTypeByName("Python");

    if (pythonFileType != null) {
      var pythonSyntaxHighlighterFactory = SyntaxHighlighterFactory.getSyntaxHighlighter(pythonFileType, project, file);
      if (pythonSyntaxHighlighterFactory != null) {
        registerLayer(RestTokenTypes.PYTHON_LINE, new LayerDescriptor(pythonSyntaxHighlighterFactory, "",
                                                                      EditorColors.INJECTED_LANGUAGE_FRAGMENT));
      }
    }

    FileType djangoTemplateFileType = FileTypeManager.getInstance().findFileTypeByName("DjangoTemplate");
    if (djangoTemplateFileType != null) {
      var djangoSyntaxHighlighterFactory = SyntaxHighlighterFactory.getSyntaxHighlighter(djangoTemplateFileType, project, file);
      if (djangoSyntaxHighlighterFactory != null) {
        registerLayer(RestTokenTypes.DJANGO_LINE, new LayerDescriptor(djangoSyntaxHighlighterFactory, "",
                                                                      EditorColors.INJECTED_LANGUAGE_FRAGMENT));
      }
    }

    var jsSyntaxHighlighterFactory = SyntaxHighlighterFactory.getSyntaxHighlighter(StdFileTypes.JS, project, file);
    if (jsSyntaxHighlighterFactory != null) {
      registerLayer(RestTokenTypes.JAVASCRIPT_LINE, new LayerDescriptor(
        jsSyntaxHighlighterFactory, "", EditorColors.INJECTED_LANGUAGE_FRAGMENT));
    }
  }
}
