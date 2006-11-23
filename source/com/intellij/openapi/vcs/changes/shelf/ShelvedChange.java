/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 23.11.2006
 * Time: 19:06:26
 */
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.vcs.FileStatus;

public class ShelvedChange {
  private String myPatchPath;
  private String myFilePath;
  private FileStatus myFileStatus;

  public ShelvedChange(final String patchPath, final String filePath, final FileStatus fileStatus) {
    myPatchPath = patchPath;
    myFilePath = filePath;
    myFileStatus = fileStatus;
  }

  public String getFilePath() {
    return myFilePath;
  }

  public String getFileName() {
    int pos = myFilePath.lastIndexOf('/');
    if (pos >= 0) return myFilePath.substring(pos+1);
    return myFilePath;
  }

  public FileStatus getFileStatus() {
    return myFileStatus;
  }
}