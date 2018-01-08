/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
                       @Nullable final AnnotationConsumer handler) throws VcsException {
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

    @Nullable
    public CommitInfo mergedCommit() {
      return merged != null && merged.commit != null ? merged.commit.build() : null;
    }
  }

  public static class MergedEntry {

    @XmlAttribute(name = "path")
    public String path;

    public CommitInfo.Builder commit;
  }
}
