package org.jetbrains.idea.svn.properties;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitPropertyClient extends BaseSvnClient implements PropertyClient {

  @Override
  public SVNPropertyData getProperty(@NotNull File path,
                                     @NotNull String property,
                                     @Nullable SVNRevision pegRevision,
                                     @Nullable SVNRevision revision) throws VcsException {
    try {
      return myVcs.createWCClient().doGetProperty(path, property, pegRevision, revision);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }
}
