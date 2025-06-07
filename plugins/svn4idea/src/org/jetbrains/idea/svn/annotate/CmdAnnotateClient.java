// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.checkin.CommitInfo;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.jetbrains.idea.svn.diff.DiffOptions;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

public class CmdAnnotateClient extends BaseSvnClient implements AnnotateClient {

  @Override
  public void annotate(@NotNull Target target,
                       @NotNull Revision startRevision,
                       @NotNull Revision endRevision,
                       boolean includeMergedRevisions,
                       @Nullable DiffOptions diffOptions,
                       final @Nullable AnnotationConsumer handler) throws VcsException {
    List<String> parameters = new ArrayList<>();
    CommandUtil.put(parameters, target);
    CommandUtil.put(parameters, startRevision, endRevision);
    CommandUtil.put(parameters, includeMergedRevisions, "--use-merge-history");
    CommandUtil.put(parameters, diffOptions);
    parameters.add("--xml");

    CommandExecutor command = execute(myVcs, target, SvnCommandName.blame, parameters, null);

    parseOutput(command.getOutput(), handler);
  }

  public void parseOutput(@NotNull String output, @Nullable AnnotationConsumer handler) throws VcsException {
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
  }

  private static void invokeHandler(@NotNull AnnotationConsumer handler, @NotNull LineEntry entry) {
    if (entry.commit != null) {
      // line numbers in our api start from 0 - not from 1 like in svn output
      handler.consume(entry.lineNumber - 1, entry.commit.build(), entry.mergedCommit());
    }
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

    public CommitInfo.Builder commit;

    @XmlElement(name = "merged")
    public MergedEntry merged;

    public @Nullable CommitInfo mergedCommit() {
      return merged != null && merged.commit != null ? merged.commit.build() : null;
    }
  }

  public static class MergedEntry {

    @XmlAttribute(name = "path")
    public String path;

    public CommitInfo.Builder commit;
  }
}
