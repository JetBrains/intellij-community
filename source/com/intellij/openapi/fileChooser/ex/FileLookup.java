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
    boolean isDirectory();


    List<LookupFile> getChildren(LookupFilter filter);

    @Nullable
    LookupFile getParent();

    boolean exists();
  }

  interface LookupFilter {
    boolean isAccepted(LookupFile file);
  }

}
