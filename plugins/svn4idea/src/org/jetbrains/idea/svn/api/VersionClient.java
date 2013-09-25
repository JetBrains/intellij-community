package org.jetbrains.idea.svn.api;

import com.intellij.openapi.util.Version;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Kolosovsky.
 */
public interface VersionClient extends SvnClient {

  @NotNull
  Version getVersion() throws VcsException;
}
