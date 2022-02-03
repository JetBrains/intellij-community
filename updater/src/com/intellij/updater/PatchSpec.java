/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.updater;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PatchSpec {
  private String myOldVersionDescription = "";
  private String myNewVersionDescription = "";
  private String myOldFolder;
  private String myNewFolder;
  private String myPatchFile;
  private String myJarFile;
  private boolean myIsBinary;
  private boolean myIsStrict;
  private List<String> myIgnoredFiles = Collections.emptyList();
  private List<String> myCriticalFiles = Collections.emptyList();
  // A conflict in an essential file makes a patch update impossible; the IDE must be reinstalled from scratch.
  private List<String> myStrictFiles = Collections.emptyList();
  private List<String> myOptionalFiles = Collections.emptyList();
  private boolean myIsNormalized;
  private Map<String, String> myWarnings = Collections.emptyMap();
  private List<String> myDeleteFiles = Collections.emptyList();
  private String myRoot = "";
  private int myTimeout = 0;

  public String getOldVersionDescription() {
    return myOldVersionDescription;
  }

  public PatchSpec setOldVersionDescription(String oldVersionDescription) {
    myOldVersionDescription = oldVersionDescription;
    return this;
  }

  public String getNewVersionDescription() {
    return myNewVersionDescription;
  }

  public PatchSpec setNewVersionDescription(String newVersionDescription) {
    myNewVersionDescription = newVersionDescription;
    return this;
  }

  public String getOldFolder() {
    return myOldFolder;
  }

  public PatchSpec setOldFolder(String oldFolder) {
    myOldFolder = oldFolder;
    return this;
  }

  public String getNewFolder() {
    return myNewFolder;
  }

  public PatchSpec setNewFolder(String newFolder) {
    myNewFolder = newFolder;
    return this;
  }

  public String getPatchFile() {
    return myPatchFile;
  }

  public PatchSpec setPatchFile(String patchFile) {
    myPatchFile = patchFile;
    return this;
  }

  public String getJarFile() {
    return myJarFile;
  }

  public PatchSpec setJarFile(String jarFile) {
    myJarFile = jarFile;
    return this;
  }

  public boolean isStrict() {
    return myIsStrict;
  }

  public PatchSpec setStrict(boolean strict) {
    myIsStrict = strict;
    return this;
  }

  public List<String> getIgnoredFiles() {
    return myIgnoredFiles;
  }

  public PatchSpec setIgnoredFiles(List<String> ignoredFiles) {
    myIgnoredFiles = ignoredFiles;
    return this;
  }

  public List<String> getCriticalFiles() {
    return myCriticalFiles;
  }

  public PatchSpec setCriticalFiles(List<String> criticalFiles) {
    myCriticalFiles = criticalFiles;
    return this;
  }

  public List<String> getStrictFiles() {
    return myStrictFiles;
  }

  public PatchSpec setStrictFiles(List<String> strictFiles) {
    myStrictFiles = strictFiles;
    return this;
  }

  public List<String> getOptionalFiles() {
    return myOptionalFiles;
  }

  public PatchSpec setOptionalFiles(List<String> optionalFiles) {
    myOptionalFiles = optionalFiles;
    return this;
  }

  public PatchSpec setBinary(boolean binary) {
    myIsBinary = binary;
    return this;
  }

  public boolean isBinary() {
    return myIsBinary;
  }

  public boolean isNormalized() {
    return myIsNormalized;
  }

  public PatchSpec setNormalized(boolean normalized) {
    myIsNormalized = normalized;
    return this;
  }

  public PatchSpec setWarnings(Map<String, String> warnings) {
    myWarnings = warnings;
    return this;
  }

  public Map<String, String> getWarnings() {
    return myWarnings;
  }

  public PatchSpec setDeleteFiles(List<String> deleteFiles) {
    myDeleteFiles = deleteFiles;
    return this;
  }

  public List<String> getDeleteFiles() {
    return myDeleteFiles;
  }

  public PatchSpec setRoot(String root) {
    myRoot = root;
    return this;
  }

  public String getRoot() {
    return myRoot;
  }

  public int getTimeout() {
    return myTimeout;
  }

  public PatchSpec setTimeout(int timeout) {
    myTimeout = timeout;
    return this;
  }
}
