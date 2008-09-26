package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class FakeRevision implements ContentRevision {
  private final FilePath myFile;

  public FakeRevision(String path) throws ChangeListManagerSerialization.OutdatedFakeRevisionException {
    final FilePath file = VcsContextFactory.SERVICE.getInstance().createFilePathOn(new File(path));
    if (file == null) throw new ChangeListManagerSerialization.OutdatedFakeRevisionException();
    myFile = file;
  }

  @Nullable
  public String getContent() { return null; }

  @NotNull
  public FilePath getFile() {
    return myFile;
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return VcsRevisionNumber.NULL;
  }
}
