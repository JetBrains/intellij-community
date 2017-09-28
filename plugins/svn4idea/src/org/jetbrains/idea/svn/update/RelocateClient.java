package org.jetbrains.idea.svn.update;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;

public interface RelocateClient extends SvnClient {

  void relocate(@NotNull File copyRoot, @NotNull SVNURL fromPrefix, @NotNull SVNURL toPrefix) throws VcsException;
}
