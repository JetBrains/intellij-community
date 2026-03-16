package com.intellij.python.processOutput.frontend.ui

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

internal val ProcessIsErrorKey = SemanticsPropertyKey<Boolean>("ProcessIsError")
internal var SemanticsPropertyReceiver.processIsError by ProcessIsErrorKey

internal val ProcessIsBackgroundKey = SemanticsPropertyKey<Boolean>("ProcessIsBackground")
internal var SemanticsPropertyReceiver.processIsBackground by ProcessIsBackgroundKey

internal val IsExpanded = SemanticsPropertyKey<Boolean>("IsExpanded")
internal var SemanticsPropertyReceiver.isExpanded by IsExpanded
