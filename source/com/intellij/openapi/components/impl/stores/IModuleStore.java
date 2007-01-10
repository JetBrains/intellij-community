package com.intellij.openapi.components.impl.stores;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface IModuleStore extends IComponentStore {
  void setModule(ModuleImpl module);
  @SuppressWarnings({"EmptyMethod"})
  boolean isSavePathsRelative();

  @SuppressWarnings({"EmptyMethod"})
  void save() throws IOException;

  ReplacePathToMacroMap getMacroReplacements();

  void setModuleFilePath(final String filePath);

  @Nullable
  VirtualFile getModuleFile();

  @NotNull
  String getModuleFilePath();

  @NotNull
  String getModuleFileName();
}
