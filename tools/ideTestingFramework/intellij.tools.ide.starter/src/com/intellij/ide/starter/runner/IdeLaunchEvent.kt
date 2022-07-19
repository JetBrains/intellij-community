// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.runner

import com.intellij.ide.starter.bus.Event
import com.intellij.ide.starter.bus.EventState

class IdeLaunchEvent(state: EventState, runContext: IDERunContext) : Event<IDERunContext>(state, runContext)