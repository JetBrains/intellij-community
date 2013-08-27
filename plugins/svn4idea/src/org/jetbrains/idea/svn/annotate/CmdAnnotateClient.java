package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommand;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.StringReader;
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
    // TODO: after merge remove setting includeMergedRevisions to false and update parsing
    includeMergedRevisions = false;

    List<String> parameters = new ArrayList<String>();
    CommandUtil.put(parameters, target.getPathOrUrlString(), pegRevision);
    parameters.add("--revision");
    parameters.add(startRevision + ":" + endRevision);
    CommandUtil.put(parameters, includeMergedRevisions, "--use-merge-history");
    CommandUtil.put(parameters, diffOptions);
    parameters.add("--xml");

    SvnCommand command = CommandUtil.execute(myVcs, SvnCommandName.blame, parameters, null);

    parseOutput(command.getOutput(), handler);
  }

  public void parseOutput(@NotNull String output, @Nullable ISVNAnnotateHandler handler) throws VcsException {
    try {
      JAXBContext context = JAXBContext.newInstance(BlameInfo.class);
      Unmarshaller unmarshaller = context.createUnmarshaller();
      BlameInfo info = (BlameInfo)unmarshaller.unmarshal(new StringReader(output));

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
    handler.handleLine(entry.date(), entry.revision(), entry.author(), null, null, 0, null, null, entry.lineNumber - 1);
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

    public long revision() {
      return commit != null ? commit.revision : 0;
    }

    public String author() {
      return commit != null ? commit.author : null;
    }

    public Date date() {
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
}
