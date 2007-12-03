/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 17.11.2006
 * Time: 17:36:42
 */
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ide.structureView.StructureViewBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PatchFileType implements FileType {

  @NotNull
  @NonNls
  public String getName() {
    return "PATCH";
  }

  @NotNull
  public String getDescription() {
    return VcsBundle.message("patch.file.type.description");
  }

  @NotNull
  @NonNls
  public String getDefaultExtension() {
    return "patch";
  }

  @Nullable
  public Icon getIcon() {
    return IconLoader.getIcon("/nodes/pointcut.png");
  }

  public boolean isBinary() {
    return false;
  }

  public boolean isReadOnly() {
    return false;
  }

  @Nullable
  @NonNls
  public String getCharset(@NotNull VirtualFile file) {
    return null;
  }

  @Nullable
  public SyntaxHighlighter getHighlighter(@Nullable Project project, final VirtualFile virtualFile) {
    return null;
  }

  @Nullable
  public StructureViewBuilder getStructureViewBuilder(@NotNull VirtualFile file, @NotNull Project project) {
    return null;
  }
}