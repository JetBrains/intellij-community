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
  private final String myServerUrl;

  public RequiredLibrariesInfo(@NonNls String serverUrl) {
    myServerUrl = serverUrl;
  }

  public RequiredLibrariesInfo() {
    this(null);
  }

  public void addLibraryInfoForRepository(@NonNls String expectedJarName,
                             @Nullable @NonNls String downloadingUrl,
                             @Nullable String repositoryUrl,
                             @NonNls String... requiredClasses) {

    myLibraryInfos.add(new LibraryInfo(expectedJarName, downloadingUrl, repositoryUrl, requiredClasses));
  }

  public void addLibraryInfo(@NonNls String expectedJarName,
                             @Nullable @NonNls String jarName,
                             @NonNls String... requiredClasses) {

    addLibraryInfoForRepository(expectedJarName, myServerUrl + jarName, myServerUrl, requiredClasses);
  }

  public void addSimpleLibraryInfo(@NonNls String expectedJarName,
                             @NonNls String... requiredClasses) {

    addLibraryInfoForRepository(expectedJarName, expectedJarName, myServerUrl, requiredClasses);
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
