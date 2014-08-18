package org.jetbrains.idea.svn.api;

import com.intellij.openapi.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

/**
 * @author Konstantin Kolosovsky.
 */
public interface VersionClient extends SvnClient {

  @NotNull
  Version getVersion() throws SvnBindException;
}
