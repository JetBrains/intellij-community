// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.statistics;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

public class ShCounterUsagesCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("sh.shell.script", 1);
  public static final EventId DOCUMENTATION_PROVIDER_USED_EVENT_ID = GROUP.registerEvent("DocumentationProviderUsed");
  public static final EventId GENERATE_ACTION_USED_EVENT_ID = GROUP.registerEvent("GenerateActionUsed");
  public static final EventId DISABLE_INSPECTION_USED_EVENT_ID = GROUP.registerEvent("DisableInspectionUsed");
  public static final EventId EXPLAIN_SHELL_USED_EVENT_ID = GROUP.registerEvent("ExplainShellUsed");
  public static final EventId FILE_PATH_COMPLETION_USED_EVENT_ID = GROUP.registerEvent("FilePathCompletionUsed");
  public static final EventId CONDITION_KEYWORD_COMPLETION_USED_EVENT_ID = GROUP.registerEvent("ConditionKeywordCompletionUsed");
  public static final EventId BASE_KEYWORD_COMPLETION_USED_EVENT_ID = GROUP.registerEvent("BaseKeywordCompletionUsed");
  public static final EventId EXTERNAL_FORMATTER_DOWNLOADED_EVENT_ID = GROUP.registerEvent("ExternalFormatterDownloaded");
  public static final EventId RENAMING_ACTION_USED_EVENT_ID = GROUP.registerEvent("RenamingActionUsed");
  public static final EventId QUICK_FIX_USED_EVENT_ID = GROUP.registerEvent("QuickFixUsed");
  public static final EventId EXTERNAL_ANNOTATOR_DOWNLOADED_EVENT_ID = GROUP.registerEvent("ExternalAnnotatorDownloaded");
  public static final EventId SUPPRESS_INSPECTION_USED_EVENT_ID = GROUP.registerEvent("SuppressInspectionUsed");

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }
}
