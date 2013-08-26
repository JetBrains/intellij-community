package org.jetbrains.idea.svn.properties;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommand;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdPropertyClient extends BaseSvnClient implements PropertyClient {

  @Nullable
  @Override
  public SVNPropertyData getProperty(@NotNull File path,
                                     @NotNull String property,
                                     boolean revisionProperty,
                                     @Nullable SVNRevision pegRevision,
                                     @Nullable SVNRevision revision)
    throws VcsException {
    List<String> parameters = new ArrayList<String>();

    parameters.add(property);
    CommandUtil.put(parameters, path, pegRevision);
    if (!revisionProperty) {
      CommandUtil.put(parameters, revision);
    } else {
      parameters.add("--revprop");
      CommandUtil.put(parameters, resolveRevisionNumber(path, revision));
    }

    SvnCommand command = CommandUtil.execute(myVcs, SvnCommandName.propget, parameters, null);

    return new SVNPropertyData(property, SVNPropertyValue.create(StringUtil.nullize(command.getOutput())), null);
  }

  private SVNRevision resolveRevisionNumber(@NotNull File path, @Nullable SVNRevision revision) throws VcsException {
    long result = revision != null ? revision.getNumber() : -1;

    // base should be resolved manually - could not set revision to BASE to get revision property
    if (SVNRevision.BASE.equals(revision)) {
      SVNInfo info = myVcs.getInfo(path, SVNRevision.BASE);

      result = info != null ? info.getRevision().getNumber() : -1;
    }

    if (result == -1) {
      throw new VcsException("Could not determine revision number for file " + path + " and revision " + revision);
    }

    return SVNRevision.create(result);
  }
}
