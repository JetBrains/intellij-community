package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsRoot;

import java.util.ArrayList;
import java.util.List;

public class DirtBuilder {
  private final VcsGuess myGuess;

  private final List<FilePathUnderVcs> myFiles;
  private final List<FilePathUnderVcs> myDirs;
  private boolean myEverythingDirty;

  public DirtBuilder(final VcsGuess guess) {
    myGuess = guess;
    myDirs = new ArrayList<FilePathUnderVcs>();
    myFiles = new ArrayList<FilePathUnderVcs>();
    myEverythingDirty = false;
  }

  public DirtBuilder(final DirtBuilder builder) {
    myGuess = builder.myGuess;
    myDirs = new ArrayList<FilePathUnderVcs>(builder.myDirs);
    myFiles = new ArrayList<FilePathUnderVcs>(builder.myFiles);
    myEverythingDirty = builder.myEverythingDirty;
  }

  public void reset() {
    myFiles.clear();
    myDirs.clear();
    myEverythingDirty = false;
  }

  public void everythingDirty() {
    myEverythingDirty = true;
  }

  public void addDirtyFile(final VcsRoot root) {
    myFiles.add(new FilePathUnderVcs(new FilePathImpl(root.path), root.vcs));
  }

  public void addDirtyDirRecursively(final VcsRoot root) {
    myDirs.add(new FilePathUnderVcs(new FilePathImpl(root.path), root.vcs));
  }

  public void addDirtyFile(final FilePathUnderVcs root) {
    myFiles.add(root);
  }

  public void addDirtyDirRecursively(final FilePathUnderVcs root) {
    myDirs.add(root);
  }

  public boolean isEverythingDirty() {
    return myEverythingDirty;
  }

  public List<FilePathUnderVcs> getFilesForVcs() {
    return myFiles;
  }

  public List<FilePathUnderVcs> getDirsForVcs() {
    return myDirs;
  }

  public boolean isEmpty() {
    return myFiles.isEmpty() && myDirs.isEmpty();
  }
}
