package com.intellij.openapi.fileChooser.ex;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface FileLookup {

  interface Finder {
    @Nullable
    LookupFile find(@NotNull String path);

    String normalize(@NotNull final String path);

    String getSeparator();
  }

  interface LookupFile {

    String getName();
    String getAbsolutePath();
    
    List<LookupFile> getChildren(LookupFilter filter);

    boolean exists();
  }

  interface LookupFilter {
    boolean isAccepted(LookupFile file);
  }

}
