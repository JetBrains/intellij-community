/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class RequiredLibrariesInfo {

  private List<LibraryInfo> myLibraryInfos = new ArrayList<LibraryInfo>();

  public RequiredLibrariesInfo() {}

  public RequiredLibrariesInfo(LibraryInfo... libs) {
    myLibraryInfos.addAll(new ArrayList<LibraryInfo>(Arrays.asList(libs)));
  }

  public void addLibraryInfo(LibraryInfo lib) {
    myLibraryInfos.add(lib);
  }

  public @Nullable RequiredClassesNotFoundInfo checkLibraries(VirtualFile[] libraryFiles) {
    List<LibraryInfo> infos = new ArrayList<LibraryInfo>();
    List<String> classes = new ArrayList<String>();

    for (LibraryInfo info : myLibraryInfos) {
      boolean notFound = false;
      for (String className : info.getRequiredClasses()) {
        if (!LibraryUtil.isClassAvailableInLibrary(libraryFiles, className)) {
          classes.add(className);
          notFound = true;
        }
      }

      if (notFound) {
        infos.add(info);
      }
    }
    if (infos.isEmpty()) {
      return null;
    }
    return new RequiredClassesNotFoundInfo(classes.toArray(new String[classes.size()]),
                                           infos.toArray(new LibraryInfo[infos.size()]));
  }

  public static class RequiredClassesNotFoundInfo {
    private String[] myClassNames;
    private LibraryInfo[] myLibraryInfos;

    public RequiredClassesNotFoundInfo(final String[] classNames, final LibraryInfo[] libraryInfos) {
      myClassNames = classNames;
      myLibraryInfos = libraryInfos;
    }


    public String[] getClassNames() {
      return myClassNames;
    }

    public LibraryInfo[] getLibraryInfos() {
      return myLibraryInfos;
    }
  }

}
