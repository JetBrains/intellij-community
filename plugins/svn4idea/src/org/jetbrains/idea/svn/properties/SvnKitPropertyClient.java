package org.jetbrains.idea.svn.properties;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitPropertyClient extends BaseSvnClient implements PropertyClient {

  @Nullable
  @Override
  public SVNPropertyData getProperty(@NotNull File path,
                                     @NotNull String property,
                                     boolean revisionProperty,
                                     @Nullable SVNRevision pegRevision,
                                     @Nullable SVNRevision revision) throws VcsException {
    try {
      if (!revisionProperty) {
        return myVcs.createWCClient().doGetProperty(path, property, pegRevision, revision);
      } else {
        return getRevisionProperty(path, property, revision);
      }
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  @Override
  public void list(@NotNull SvnTarget target,
                   @Nullable SVNRevision revision,
                   @Nullable SVNDepth depth,
                   @Nullable ISVNPropertyHandler handler) throws VcsException {
    SVNWCClient client = myVcs.createWCClient();

    try {
      if (target.isURL()) {
        client.doGetProperty(target.getURL(), null, target.getPegRevision(), revision, depth, handler);
      } else {
        client.doGetProperty(target.getFile(), null, target.getPegRevision(), revision, depth, handler, null);
      }
    } catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  private SVNPropertyData getRevisionProperty(@NotNull File path, @NotNull final String property, @Nullable SVNRevision revision) throws SVNException{
    final SVNWCClient client = myVcs.createWCClient();
    final SVNPropertyData[] result = new SVNPropertyData[1];

    client.doGetRevisionProperty(path, null, revision, new ISVNPropertyHandler() {
      @Override
      public void handleProperty(File path, SVNPropertyData property) throws SVNException {
        handle(property);
      }

      @Override
      public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
        handle(property);
      }

      @Override
      public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
        handle(property);
      }

      private void handle(@NotNull SVNPropertyData data) {
        if (property.equals(data.getName())) {
          result[0] = data;
        }
      }
    });
    return result[0];
  }
}
