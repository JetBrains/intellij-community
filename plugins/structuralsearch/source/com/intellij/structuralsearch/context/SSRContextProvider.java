package com.intellij.structuralsearch.context;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public interface SSRContextProvider {
  ExtensionPointName<SSRContextProvider> EP_NAME = ExtensionPointName.create("com.intellij.structuralsearch.contextProvider");

  @Nullable
  String getContext(@NotNull FileType fileType, @NotNull String pattern);
}
