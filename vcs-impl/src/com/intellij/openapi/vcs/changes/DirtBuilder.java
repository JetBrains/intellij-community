package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsRoot;

import java.util.ArrayList;
import java.util.List;

public class DirtBuilder {
  private final VcsGuess myGuess;

  private final FilesAndRoots myFiles;
  private final FilesAndRoots myDirs;

  public DirtBuilder(final VcsGuess guess) {
    myGuess = guess;
    myDirs = new FilesAndRoots();
    myFiles = new FilesAndRoots();
  }

  public void addDirtyDirRecursively(final FilePath newcomer) {
    myDirs.put(newcomer);
  }

  public void addDirtyFile(final VcsRoot root) {
    myFiles.put(root);
  }

  public void addDirtyFile(final FilePath root) {
    myFiles.put(root);
  }

  public void addDirtyDirRecursively(final VcsRoot root) {
    myDirs.put(root);
  }

  public List<MyVcsRoot> getFilesForVcs() {
    return myFiles.guessAndGet(myGuess);
  }

  public List<MyVcsRoot> getDirsForVcs() {
    return myDirs.guessAndGet(myGuess);
  }

  public boolean isEmpty() {
    return myFiles.myFiles.isEmpty() && myFiles.myRoots.isEmpty() && myDirs.myFiles.isEmpty() && myDirs.myRoots.isEmpty();
  }

  public boolean correct() {
    return myFiles.isCorrect(myGuess) && myDirs.isCorrect(myGuess);
  }

  private static class FilesAndRoots {
    private final List<FilePath> myFiles;
    private final List<MyVcsRoot> myRoots;
    private boolean myConverted;
    private boolean myCorrect;

    private FilesAndRoots() {
      myFiles = new ArrayList<FilePath>();
      myRoots = new ArrayList<MyVcsRoot>();
      myCorrect = true;
      myConverted = false;
    }

    void put(final FilePath path) {
      myFiles.add(path);
    }

    void put(final VcsRoot root) {
      myRoots.add(new MyVcsRoot(root));
    }

    List<MyVcsRoot> guessAndGet(final VcsGuess guess) {
      convertation(guess);
      return myRoots;
    }

    private void convertation(final VcsGuess guess) {
      for (FilePath file : myFiles) {
        final AbstractVcs vcs = guess.getVcsForDirty(file);
        if (vcs != null) {
          myRoots.add(new MyVcsRoot(file, vcs));
        } else {
          myCorrect = false;
        }
      }
      myFiles.clear();
      myConverted = true;
    }

    public boolean isCorrect(final VcsGuess guess) {
      if (! myConverted) {
        convertation(guess);
      }
      return myCorrect;
    }
  }

  static class MyVcsRoot {
    private final FilePath myPath;
    private final AbstractVcs myVcs;

    MyVcsRoot(final FilePath path, final AbstractVcs vcs) {
      myPath = path;
      myVcs = vcs;
    }

    MyVcsRoot(final VcsRoot root) {
      myPath = new FilePathImpl(root.path);
      myVcs = root.vcs;
    }

    public FilePath getPath() {
      return myPath;
    }

    public AbstractVcs getVcs() {
      return myVcs;
    }
  }
}
