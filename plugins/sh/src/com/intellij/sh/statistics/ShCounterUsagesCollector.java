// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.statistics;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

public class ShCounterUsagesCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("shell.script", 1);
  public static final EventId DOCUMENTATION_PROVIDER_USED_EVENT_ID = GROUP.registerEvent("DocumentationProviderUsed");
  public static final EventId CONDITION_KEYWORD_COMPLETION_USED_EVENT_ID = GROUP.registerEvent("ConditionKeywordCompletionUsed");
  public static final EventId BASE_KEYWORD_COMPLETION_USED_EVENT_ID = GROUP.registerEvent("BaseKeywordCompletionUsed");
  public static final EventId EXTERNAL_FORMATTER_DOWNLOADED_EVENT_ID = GROUP.registerEvent("ExternalFormatterDownloaded");
  public static final EventId EXTERNAL_ANNOTATOR_DOWNLOADED_EVENT_ID = GROUP.registerEvent("ExternalAnnotatorDownloaded");

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }
}
