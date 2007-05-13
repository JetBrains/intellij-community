/*
 * Created by IntelliJ IDEA.
 * User: valentin
 * Date: 29.01.2004
 * Time: 21:10:56
 */
package com.intellij.openapi.fileTypes.ex;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class FakeFileType implements FileType {
  public abstract boolean isMyFileType(VirtualFile file);

  @NotNull
  public String getDefaultExtension() {
    return "fakeExtension";
  }

  public Icon getIcon() {
    return null;
  }

  public boolean isBinary() {
    return true;
  }

  public boolean isReadOnly() {
    return true;
  }

  public String getCharset(@NotNull VirtualFile file) {
    return null;
  }

  public StructureViewBuilder getStructureViewBuilder(@NotNull VirtualFile file, @NotNull Project project) {
    return null;
  }

}