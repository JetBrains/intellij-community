// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.StandardProgressIndicator
import com.intellij.openapi.progress.WrappedProgressIndicator

// Copied from com.intellij.ide.util.DelegatingProgressIndicator
open class J2KDelegatingProgressIndicator(indicator: ProgressIndicator) : WrappedProgressIndicator, StandardProgressIndicator {
    protected val delegate: ProgressIndicator = indicator

    override fun start() = delegate.start()
    override fun stop() = delegate.stop()
    override fun isRunning() = delegate.isRunning
    override fun cancel() = delegate.cancel()
    override fun isCanceled() = delegate.isCanceled

    override fun setText(text: String?) {
        delegate.text = text
    }

    override fun getText(): String? = delegate.text

    override fun setText2(text: String?) {
        delegate.text2 = text
    }

    override fun getText2(): String? = delegate.text2
    override fun getFraction() = delegate.fraction

    override fun setFraction(fraction: Double) {
        delegate.fraction = fraction
    }

    override fun pushState() = delegate.pushState()
    override fun popState() = delegate.popState()
    override fun isModal() = delegate.isModal
    override fun getModalityState() = delegate.modalityState

    override fun setModalityProgress(modalityProgress: ProgressIndicator?) {
        delegate.setModalityProgress(modalityProgress)
    }

    override fun isIndeterminate() = delegate.isIndeterminate

    override fun setIndeterminate(indeterminate: Boolean) {
        delegate.isIndeterminate = indeterminate
    }

    override fun checkCanceled() = delegate.checkCanceled()
    override fun getOriginalProgressIndicator() = delegate
    override fun isPopupWasShown() = delegate.isPopupWasShown
    override fun isShowing() = delegate.isShowing
}