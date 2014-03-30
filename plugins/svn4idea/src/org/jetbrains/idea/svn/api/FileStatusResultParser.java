package org.jetbrains.idea.svn.api;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Konstantin Kolosovsky.
 */
public class FileStatusResultParser {

  private static final double DEFAULT_PROGRESS = 0.0;

  @NotNull
  private Pattern myLinePattern;

  @Nullable
  private ISVNEventHandler handler;

  @NotNull
  private Convertor<Matcher, SVNEvent> myConvertor;

  public FileStatusResultParser(@NotNull Pattern linePattern,
                                @Nullable ISVNEventHandler handler,
                                @NotNull Convertor<Matcher, SVNEvent> convertor) {
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
        handler.handleEvent(myConvertor.convert(matcher), DEFAULT_PROGRESS);
      } catch (SVNException e) {
        throw new VcsException(e);
      }
    }
  }
}
