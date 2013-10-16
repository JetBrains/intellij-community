package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdAnnotateClient extends BaseSvnClient implements AnnotateClient {

  @Override
  public void annotate(@NotNull SvnTarget target,
                       @NotNull SVNRevision startRevision,
                       @NotNull SVNRevision endRevision,
                       @Nullable SVNRevision pegRevision,
                       boolean includeMergedRevisions,
                       @Nullable SVNDiffOptions diffOptions,
                       @Nullable final ISVNAnnotateHandler handler) throws VcsException {
    List<String> parameters = new ArrayList<String>();
    CommandUtil.put(parameters, target.getPathOrUrlString(), pegRevision);
    parameters.add("--revision");
    parameters.add(startRevision + ":" + endRevision);
    CommandUtil.put(parameters, includeMergedRevisions, "--use-merge-history");
    CommandUtil.put(parameters, diffOptions);
    parameters.add("--xml");

    CommandExecutor command = CommandUtil.execute(myVcs, target, SvnCommandName.blame, parameters, null);

    parseOutput(command.getOutput(), handler);
  }

  public void parseOutput(@NotNull String output, @Nullable ISVNAnnotateHandler handler) throws VcsException {
    try {
      BlameInfo info = CommandUtil.parse(output, BlameInfo.class);

      if (handler != null && info != null && info.target != null && info.target.lineEntries != null) {
        for (LineEntry entry : info.target.lineEntries) {
          invokeHandler(handler, entry);
        }
      }
    }
    catch (JAXBException e) {
      throw new VcsException(e);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  private static void invokeHandler(ISVNAnnotateHandler handler, LineEntry entry) throws SVNException {
    // line numbers in our api start from 0 - not from 1 like in svn output
    // "line" value is not used in handlers - so null is passed
    handler
      .handleLine(entry.date(), entry.revision(), entry.author(), null, entry.mergedDate(), entry.mergedRevision(), entry.mergedAuthor(),
                  entry.mergedPath(), entry.lineNumber - 1);
  }

  @XmlRootElement(name = "blame")
  public static class BlameInfo {

    @XmlElement(name = "target")
    public TargetEntry target;
  }

  public static class TargetEntry {

    @XmlElement(name = "entry")
    List<LineEntry> lineEntries;
  }

  public static class LineEntry {

    @XmlAttribute(name = "line-number")
    public int lineNumber;

    @XmlElement(name = "commit")
    public CommitEntry commit;

    @XmlElement(name = "merged")
    public MergedEntry merged;

    public long revision() {
      return revision(commit);
    }

    @Nullable
    public String author() {
      return author(commit);
    }

    @Nullable
    public Date date() {
      return date(commit);
    }

    @Nullable
    public String mergedPath() {
      return merged != null ? merged.path : null;
    }

    public long mergedRevision() {
      return merged != null ? revision(merged.commit) : 0;
    }

    @Nullable
    public String mergedAuthor() {
      return merged != null ? author(merged.commit) : null;
    }

    @Nullable
    public Date mergedDate() {
      return merged != null ? date(merged.commit) : null;
    }

    private static long revision(@Nullable CommitEntry commit) {
      return commit != null ? commit.revision : 0;
    }

    @Nullable
    private static String author(@Nullable CommitEntry commit) {
      return commit != null ? commit.author : null;
    }

    @Nullable
    private static Date date(@Nullable CommitEntry commit) {
      return commit != null ? commit.date : null;
    }
  }

  public static class CommitEntry {

    @XmlAttribute(name = "revision")
    public long revision;

    @XmlElement(name = "author")
    public String author;

    @XmlElement(name = "date")
    public Date date;
  }

  public static class MergedEntry {

    @XmlAttribute(name = "path")
    public String path;

    @XmlElement(name = "commit")
    public CommitEntry commit;
  }
}
