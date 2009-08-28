package com.intellij.openapi.vcs;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public interface VcsOutgoingChangesProvider <T extends CommittedChangeList> extends VcsProviderMarker {
  List<T> getOutgoingChanges(final VirtualFile vcsRoot) throws VcsException;
}
