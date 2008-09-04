package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface LibrariesContainer {

  @Nullable
  Project getProject();

  enum LibraryLevel {GLOBAL, PROJECT, MODULE}

  @NotNull
  Library[] getLibraies(@NotNull LibraryLevel libraryLevel);

  @NotNull
  Library[] getAllLibraries();

  @NotNull
  VirtualFile[] getLibraryFiles(@NotNull Library library, @NotNull OrderRootType rootType);

  boolean canCreateLibrary(@NotNull LibraryLevel level);

  Library createLibrary(@NotNull @NonNls String name, @NotNull LibraryLevel level,
                        @NotNull VirtualFile[] classRoots, @NotNull VirtualFile[] sourceRoots);
}
