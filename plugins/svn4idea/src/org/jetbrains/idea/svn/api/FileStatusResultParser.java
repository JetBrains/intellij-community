package org.jetbrains.idea.svn.api;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Konstantin Kolosovsky.
 */
public class FileStatusResultParser {

  @NotNull
  private Pattern myLinePattern;

  @Nullable
  private ProgressTracker handler;

  @NotNull
  private Convertor<Matcher, ProgressEvent> myConvertor;

  public FileStatusResultParser(@NotNull Pattern linePattern,
                                @Nullable ProgressTracker handler,
                                @NotNull Convertor<Matcher, ProgressEvent> convertor) {
    myLinePattern = linePattern;
    this.handler = handler;
    myConvertor = convertor;
  }

  public void parse(@NotNull String output) throws VcsException {
    if (StringUtil.isEmpty(output)) {
      return;
    }

    for (String line : StringUtil.splitByLines(output)) {
      onLine(line);
    }
  }

  public void onLine(@NotNull String line) throws VcsException {
    Matcher matcher = myLinePattern.matcher(line);
    if (matcher.matches()) {
      process(matcher);
    }
    else {
      throw new VcsException("unknown state on line " + line);
    }
  }

  public void process(@NotNull Matcher matcher) throws VcsException {
    if (handler != null) {
      try {
        handler.consume(myConvertor.convert(matcher));
      } catch (SVNException e) {
        throw new VcsException(e);
      }
    }
  }
}
