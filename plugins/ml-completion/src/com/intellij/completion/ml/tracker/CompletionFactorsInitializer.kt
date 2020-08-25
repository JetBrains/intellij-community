// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.tracker

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.completion.ml.personalization.UserFactorDescriptions
import com.intellij.completion.ml.personalization.UserFactorStorage
import com.intellij.completion.ml.personalization.UserFactorsManager
import com.intellij.completion.ml.personalization.session.SessionFactorsUtils
import com.intellij.completion.ml.personalization.session.SessionPrefixTracker
import com.intellij.completion.ml.storage.MutableLookupStorage

class CompletionFactorsInitializer : LookupTracker() {
  companion object {
    var isEnabledInTests: Boolean = false
  }

  override fun lookupCreated(lookup: LookupImpl, storage: MutableLookupStorage) {
    if (ApplicationManager.getApplication().isUnitTestMode && !isEnabledInTests) return

    processUserFactors(lookup)
    processSessionFactors(lookup, storage)
  }

  private fun shouldUseUserFactors() = UserFactorsManager.ENABLE_USER_FACTORS

  private fun shouldUseSessionFactors(): Boolean = SessionFactorsUtils.shouldUseSessionFactors()

  private fun processUserFactors(lookup: LookupImpl) {
    if (!shouldUseUserFactors()) return

    UserFactorStorage.applyOnBoth(lookup.project, UserFactorDescriptions.COMPLETION_USAGE) {
      it.fireCompletionUsed()
    }

    // setPrefixChangeListener has addPrefixChangeListener semantics
    lookup.setPrefixChangeListener(TimeBetweenTypingTracker(lookup.project))
    lookup.addLookupListener(LookupCompletedTracker())
    lookup.addLookupListener(LookupStartedTracker())
  }

  private fun processSessionFactors(lookup: LookupImpl, lookupStorage: MutableLookupStorage) {
    if (!shouldUseSessionFactors()) return

    lookup.setPrefixChangeListener(SessionPrefixTracker(lookupStorage.sessionFactors))
    lookup.addLookupListener(LookupSelectionTracker(lookupStorage))
  }
}