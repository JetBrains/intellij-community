// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.events

import com.intellij.tools.ide.starter.bus.shared.events.SharedEvent


class StopProfilerEvent(val data: List<String>) : SharedEvent()