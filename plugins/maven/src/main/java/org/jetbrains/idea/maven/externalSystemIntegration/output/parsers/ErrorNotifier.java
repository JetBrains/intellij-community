// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import com.intellij.build.events.MessageEvent;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ErrorNotifier extends MessageNotifier {
  private final Set<String> myMessagesToSkip = new HashSet<>();

  public ErrorNotifier() {
    super(LogMessageType.ERROR, MessageEvent.Kind.ERROR, "Error");
    myMessagesToSkip.add("one error found");
    myMessagesToSkip.add("two errors found");
    myMessagesToSkip.add("COMPILATION ERROR :");
  }

  @NotNull
  @Override
  protected String getMessage(String line, List<MavenLogEntryReader.MavenLogEntry> toConcat) {

    String message = super.getMessage(line, toConcat);
    if (StringUtil.isEmptyOrSpaces(message)) {
      return "";
    }
    message = message.replace("-> [Help 1]", "");
    if (myMessagesToSkip.contains(message.trim())) {
      return "";
    }
    return message;
  }
}

