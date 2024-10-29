// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.EditorHighlighterProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.highlighting.PythonEditorHighlighter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PyEditorHighlighterProvider implements EditorHighlighterProvider {
  @Override
  public EditorHighlighter getEditorHighlighter(@Nullable Project project,
                                                @NotNull FileType fileType, @Nullable VirtualFile virtualFile,
                                                @NotNull EditorColorsScheme colors) {
    return new PythonEditorHighlighter(colors, project, virtualFile);
  }
}
