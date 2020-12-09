// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.utils

import circlet.platform.api.KDateTime
import circlet.platform.api.millis
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.text.JBDateFormat

@NlsSafe
internal fun Long.formatPrettyDateTime(): String = JBDateFormat.getFormatter().formatPrettyDateTime(this)

@NlsSafe
internal fun KDateTime.formatPrettyDateTime(): String = this.millis.formatPrettyDateTime()

