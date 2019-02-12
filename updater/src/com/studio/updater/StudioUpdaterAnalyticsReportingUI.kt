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
package com.studio.updater

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ProductDetails
import com.google.wireless.android.sdk.stats.StudioPatchUpdaterEvent
import com.intellij.updater.OperationCancelledException
import com.intellij.updater.UpdaterUI
import com.intellij.updater.ValidationResult

/** A delegating Updater UI that reports events to Android Studio analytics for opted-in users. */
class StudioUpdaterAnalyticsReportingUI(private val myDelegate: UpdaterUI) : UpdaterUI {

  override fun setDescription(oldBuildDesc: String, newBuildDesc: String) {
    UsageTracker.version = newBuildDesc

    UsageTracker.log(
      AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.STUDIO_PATCH_UPDATER
        productDetails = ProductDetails.newBuilder().apply {
          product = ProductDetails.ProductKind.STUDIO_PATCH_UPDATER
          version = newBuildDesc
        }.build()

        studioPatchUpdaterEvent = StudioPatchUpdaterEvent.newBuilder().apply {
          kind = StudioPatchUpdaterEvent.Kind.PATCH_DETAILS_SHOW
          patch = StudioPatchUpdaterEvent.Patch.newBuilder().apply {
            studioVersionFrom = oldBuildDesc
            studioVersionTo = newBuildDesc
          }.build()
        }.build()
      }
    )
    myDelegate.setDescription(oldBuildDesc, newBuildDesc)
  }

  override fun setDescription(text: String) {
    myDelegate.setDescription(text)
  }

  override fun startProcess(title: String) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.STUDIO_PATCH_UPDATER
        studioPatchUpdaterEvent = StudioPatchUpdaterEvent.newBuilder().apply {
          kind = toAnalytics(title)
        }.build()
      })
    myDelegate.startProcess(title)
  }

  override fun setProgress(percentage: Int) {
    myDelegate.setProgress(percentage)
  }

  override fun setProgressIndeterminate() {
    myDelegate.setProgressIndeterminate()
  }

  @Throws(OperationCancelledException::class)
  override fun checkCancelled() {
    myDelegate.checkCancelled()
  }

  override fun showError(message: String) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.STUDIO_PATCH_UPDATER
        studioPatchUpdaterEvent = StudioPatchUpdaterEvent.newBuilder().apply {
          kind = StudioPatchUpdaterEvent.Kind.FATAL_ERROR_DIALOG_SHOW
        }.build()
      })
    myDelegate.showError(message)
  }

  @Throws(OperationCancelledException::class)
  override fun askUser(message: String) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.STUDIO_PATCH_UPDATER
        studioPatchUpdaterEvent = StudioPatchUpdaterEvent.newBuilder().apply {
          kind = StudioPatchUpdaterEvent.Kind.RETRYABLE_ERROR_DIALOG_SHOW
        }.build()
      })
    myDelegate.askUser(message)
  }

  @Throws(OperationCancelledException::class)
  override fun askUser(validationResults: List<ValidationResult>): Map<String, ValidationResult.Option> {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.STUDIO_PATCH_UPDATER
        studioPatchUpdaterEvent = StudioPatchUpdaterEvent.newBuilder().apply {
          kind = StudioPatchUpdaterEvent.Kind.VALIDATION_PROBLEMS_DIALOG_SHOW
          issueDialog = toAnalytics(validationResults)
        }.build()
      })
    val result = myDelegate.askUser(validationResults)
    UsageTracker.log(
      AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.STUDIO_PATCH_UPDATER
        studioPatchUpdaterEvent = StudioPatchUpdaterEvent.newBuilder().apply {
          kind = StudioPatchUpdaterEvent.Kind.VALIDATION_PROBLEMS_DIALOG_CLOSE
          issueDialogChoices = toAnalytics(result)
        }.build()
      })
    return result
  }

  override fun bold(text: String): String {
    return myDelegate.bold(text)
  }
}
