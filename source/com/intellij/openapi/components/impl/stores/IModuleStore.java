package com.intellij.openapi.components.impl.stores;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface IModuleStore extends IComponentStore {
  @SuppressWarnings({"EmptyMethod"})
  boolean isSavePathsRelative();

  @SuppressWarnings({"EmptyMethod"})
  void save() throws IOException;

  //todo: inline
  ReplacePathToMacroMap getMacroReplacements();

  void setModuleFilePath(final String filePath);

  @Nullable
  VirtualFile getModuleFile();

  @NotNull
  String getModuleFilePath();

  @NotNull
  String getModuleFileName();
}
