package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public interface LibrariesContainer {
  enum LibraryLevel {GLOBAL, PROJECT, MODULE}

  @NotNull
  Library[] getAllLibraries();

  @NotNull
  VirtualFile[] getLibraryFiles(@NotNull Library library, @NotNull OrderRootType rootType);

  boolean canCreateLibrary(@NotNull LibraryLevel level);

  Library createLibrary(@NotNull @NonNls String name, @NotNull LibraryLevel level,
                        @NotNull VirtualFile[] classRoots, @NotNull VirtualFile[] sourceRoots);
}
