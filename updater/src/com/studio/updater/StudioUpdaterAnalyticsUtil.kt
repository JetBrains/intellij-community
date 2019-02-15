/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("StudioUpdaterAnalyticsUtil")

package com.studio.updater


import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.UsageTracker
import com.android.utils.StdLogger
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.StudioPatchUpdaterEvent
import com.intellij.updater.ValidationResult
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

fun logProcessStart() {
  AnalyticsSettings.initialize(StdLogger(StdLogger.Level.VERBOSE))
  UsageTracker.initialize(ScheduledThreadPoolExecutor(0))
  UsageTracker.setMaxJournalTime(10, TimeUnit.MINUTES)
  UsageTracker.maxJournalSize = 1000

  UsageTracker.log(
    AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.STUDIO_PATCH_UPDATER
      studioPatchUpdaterEvent = StudioPatchUpdaterEvent.newBuilder().apply {
        kind = StudioPatchUpdaterEvent.Kind.START
      }.build()
    })
  // Ensure all events are flushed to disk before process exit.
  Runtime.getRuntime().addShutdownHook(Thread(UsageTracker::deinitialize))
}

fun logProcessSuccess() {
  UsageTracker.log(
    AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.STUDIO_PATCH_UPDATER
      studioPatchUpdaterEvent = StudioPatchUpdaterEvent.newBuilder().apply {
        kind = StudioPatchUpdaterEvent.Kind.EXIT_OK
      }.build()
    })
}

fun logProcessAbort() {
  UsageTracker.log(
    AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.STUDIO_PATCH_UPDATER
      studioPatchUpdaterEvent = StudioPatchUpdaterEvent.newBuilder().apply {
        kind = StudioPatchUpdaterEvent.Kind.EXIT_ABORT
      }.build()
    })
}

fun logException() {
  UsageTracker.log(
    AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.STUDIO_PATCH_UPDATER
      studioPatchUpdaterEvent = StudioPatchUpdaterEvent.newBuilder().apply {
        kind = StudioPatchUpdaterEvent.Kind.EXIT_EXCEPTION
      }.build()
    })
}

private fun toAnalytics(kind: ValidationResult.Kind): StudioPatchUpdaterEvent.IssueDialog.Issue.Kind {
  return when (kind) {
    ValidationResult.Kind.INFO -> StudioPatchUpdaterEvent.IssueDialog.Issue.Kind.INFO
    ValidationResult.Kind.CONFLICT -> StudioPatchUpdaterEvent.IssueDialog.Issue.Kind.CONFLICT
    ValidationResult.Kind.ERROR -> StudioPatchUpdaterEvent.IssueDialog.Issue.Kind.ERROR
  }
}

private fun toAnalytics(action: ValidationResult.Action): StudioPatchUpdaterEvent.IssueDialog.Issue.Action {
  return when (action) {
    ValidationResult.Action.CREATE -> StudioPatchUpdaterEvent.IssueDialog.Issue.Action.CREATE
    ValidationResult.Action.UPDATE -> StudioPatchUpdaterEvent.IssueDialog.Issue.Action.UPDATE
    ValidationResult.Action.DELETE -> StudioPatchUpdaterEvent.IssueDialog.Issue.Action.DELETE
    ValidationResult.Action.NO_ACTION -> StudioPatchUpdaterEvent.IssueDialog.Issue.Action.NO_ACTION
    ValidationResult.Action.VALIDATE -> StudioPatchUpdaterEvent.IssueDialog.Issue.Action.VALIDATE
  }
}

private fun toAnalytics(result: ValidationResult): StudioPatchUpdaterEvent.IssueDialog.Issue.Builder {
  return StudioPatchUpdaterEvent.IssueDialog.Issue.newBuilder().apply {
    action = toAnalytics(result.action)
    kind = toAnalytics(result.kind)
    result.options.forEach { addPresentedOption(toAnalytics(it)) }
  }
}

private fun toAnalytics(value: ValidationResult.Option): StudioPatchUpdaterEvent.ValidationOption {
  return when (value) {
    ValidationResult.Option.NONE -> StudioPatchUpdaterEvent.ValidationOption.NONE
    ValidationResult.Option.IGNORE -> StudioPatchUpdaterEvent.ValidationOption.IGNORE
    ValidationResult.Option.KEEP -> StudioPatchUpdaterEvent.ValidationOption.KEEP
    ValidationResult.Option.REPLACE -> StudioPatchUpdaterEvent.ValidationOption.REPLACE
    ValidationResult.Option.DELETE -> StudioPatchUpdaterEvent.ValidationOption.DELETE
    ValidationResult.Option.KILL_PROCESS -> StudioPatchUpdaterEvent.ValidationOption.KILL_PROCESS
  }
}

fun logProcessFinish(result: Boolean) {
  if (result) {
    logProcessSuccess()
  }
  else {
    logProcessAbort()
  }
}

internal fun toAnalytics(phase: String): StudioPatchUpdaterEvent.Kind {
  return when (phase) {
    "Extracting patch file...", "Extracting patch files..." -> StudioPatchUpdaterEvent.Kind.PHASE_EXTRACTING_PATCH_FILES
    "Validating installation..." -> StudioPatchUpdaterEvent.Kind.PHASE_VALIDATING_INSTALLATION
    "Backing up files..." -> StudioPatchUpdaterEvent.Kind.PHASE_BACKING_UP_FILES
    "Preparing update..." -> StudioPatchUpdaterEvent.Kind.PHASE_PREPARING_UPDATE
    "Applying patch..." -> StudioPatchUpdaterEvent.Kind.PHASE_APPLYING_PATCH
    "Reverting..." -> StudioPatchUpdaterEvent.Kind.PHASE_REVERTING
    "Cleaning up..." -> StudioPatchUpdaterEvent.Kind.PHASE_CLEANING_UP
    else -> StudioPatchUpdaterEvent.Kind.PHASE_UNKNOWN
  }
}

internal fun toAnalytics(results: List<ValidationResult>): StudioPatchUpdaterEvent.IssueDialog {
  return StudioPatchUpdaterEvent.IssueDialog.newBuilder().apply {
    results.forEach { addIssue(toAnalytics(it)) }
  }.build()
}

internal fun toAnalytics(result: Map<String, ValidationResult.Option>): StudioPatchUpdaterEvent.IssueDialogChoices {
  return StudioPatchUpdaterEvent.IssueDialogChoices.newBuilder().apply {
    result.values.forEach { addChoice(validationToAnalytics(it)) }
  }.build()
}

private fun validationToAnalytics(value: ValidationResult.Option): StudioPatchUpdaterEvent.IssueDialogChoices.Choice {
  return StudioPatchUpdaterEvent.IssueDialogChoices.Choice.newBuilder().apply {
    chosenOption = toAnalytics(value)
  }.build()
}