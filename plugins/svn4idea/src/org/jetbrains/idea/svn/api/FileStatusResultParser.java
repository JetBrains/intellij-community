// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.api;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jetbrains.idea.svn.SvnBundle.message;

/**
 * @author Konstantin Kolosovsky.
 */
public class FileStatusResultParser {

  @NotNull
  private final Pattern myLinePattern;

  @Nullable
  private final ProgressTracker handler;

  @NotNull
  private final Convertor<Matcher, ProgressEvent> myConvertor;

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
      throw new VcsException(message("error.parse.file.status.unknown.state.on.line", line));
    }
  }

  public void process(@NotNull Matcher matcher) throws VcsException {
    if (handler != null) {
      handler.consume(myConvertor.convert(matcher));
    }
  }
}
