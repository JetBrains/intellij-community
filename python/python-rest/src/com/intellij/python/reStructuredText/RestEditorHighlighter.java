// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText;

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
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Enables python highlighting for Rest files
 *
 * User : catherine
 */
public class RestEditorHighlighter extends LayeredLexerEditorHighlighter {

  public RestEditorHighlighter(@NotNull EditorColorsScheme scheme, @Nullable Project project, @Nullable VirtualFile file) {
    super(SyntaxHighlighterFactory.getSyntaxHighlighter(RestLanguage.INSTANCE, project, file), scheme);

    registerLayer(RestTokenTypes.PYTHON_LINE, new LayerDescriptor(
      SyntaxHighlighterFactory.getSyntaxHighlighter(PythonFileType.INSTANCE, project, file), "", EditorColors.INJECTED_LANGUAGE_FRAGMENT));

    FileType djangoTemplateFileType = FileTypeManager.getInstance().findFileTypeByName("DjangoTemplate");
    if (djangoTemplateFileType != null) {
      registerLayer(RestTokenTypes.DJANGO_LINE, new LayerDescriptor(
        SyntaxHighlighterFactory.getSyntaxHighlighter(djangoTemplateFileType, project, file), "",
        EditorColors.INJECTED_LANGUAGE_FRAGMENT));
    }

    registerLayer(RestTokenTypes.JAVASCRIPT_LINE, new LayerDescriptor(
      SyntaxHighlighterFactory.getSyntaxHighlighter(StdFileTypes.JS, project, file), "", EditorColors.INJECTED_LANGUAGE_FRAGMENT));
  }
}
