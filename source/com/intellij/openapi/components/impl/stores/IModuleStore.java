package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface IModuleStore extends IComponentStore {
  @SuppressWarnings({"EmptyMethod"})
  boolean isSavePathsRelative();

  @SuppressWarnings({"EmptyMethod"})
  void save() throws IOException;

  void setModuleFilePath(final String filePath);

  @Nullable
  VirtualFile getModuleFile();

  @NotNull
  String getModuleFilePath();

  @NotNull
  String getModuleFileName();

  Element saveToXml(final Element targetRoot, final VirtualFile configFile);
  void loadSavedConfiguration() throws JDOMException, IOException, InvalidDataException;

  void setSavePathsRelative(final boolean b);
}
