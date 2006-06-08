/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.jsf.actions;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.j2ee.ClassUtil;

/**
 * @author nik
 */
public class RequiredLibrariesInfo {
  private List<LibraryInfo> myLibraryInfos = new ArrayList<LibraryInfo>();

  public void addLibraryInfo(@NonNls String expectedJarName, @Nullable @NonNls String downloadingUrl, @Nullable String presentableUrl, 
                             @NonNls String... requiredClasses) {
    myLibraryInfos.add(new LibraryInfo(expectedJarName, downloadingUrl, presentableUrl, requiredClasses));
  }

  public @Nullable RequiredClassesNotFoundInfo checkLibraries(VirtualFile[] libraryFiles) {
    List<LibraryInfo> infos = new ArrayList<LibraryInfo>();
    List<String> classes = new ArrayList<String>();

    for (LibraryInfo info : myLibraryInfos) {
      boolean notFound = false;
      for (String className : info.getRequiredClasses()) {
        if (!ClassUtil.isClassAvailableInLibrary(libraryFiles, className)) {
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
