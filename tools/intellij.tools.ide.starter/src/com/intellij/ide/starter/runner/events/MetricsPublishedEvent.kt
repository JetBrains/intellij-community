// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.runner.events

import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.starter.bus.events.Event

/**
 * `MetricsPublisher.publish` posts this event right after it publishes the metrics of [runContext].
 * It waits until the subscribers process the event.
 */
class MetricsPublishedEvent(val runContext: IDERunContext) : Event()
