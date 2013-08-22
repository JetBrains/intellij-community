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
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdPropertyClient extends BaseSvnClient implements PropertyClient {

  @Override
  public SVNPropertyData getProperty(@NotNull File path,
                                     @NotNull String property,
                                     @Nullable SVNRevision pegRevision,
                                     @Nullable SVNRevision revision)
    throws VcsException {
    List<String> parameters = new ArrayList<String>();

    parameters.add(property);
    CommandUtil.put(parameters, path, pegRevision);
    if (revision != null && !SVNRevision.UNDEFINED.equals(revision) && !SVNRevision.WORKING.equals(revision)) {
      parameters.add("--revision");
      parameters.add(revision.toString());
    }

    SvnCommand command = CommandUtil.execute(myVcs, SvnCommandName.propget, parameters, null);

    return new SVNPropertyData(property, SVNPropertyValue.create(StringUtil.nullize(command.getOutput())), null);
  }
}
