/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.rest;

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
