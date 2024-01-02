// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.EditorHighlighterProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public final class RestEditorHighlighterProvider implements EditorHighlighterProvider  {
  @Override
  public EditorHighlighter getEditorHighlighter(@Nullable Project project,
                                                @NotNull FileType fileType,
                                                @Nullable VirtualFile virtualFile,
                                                @NotNull EditorColorsScheme colors) {
    return new RestEditorHighlighter(colors, project, virtualFile);
  }
}
