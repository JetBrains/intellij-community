// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.telemetry

import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import org.jetbrains.idea.maven.utils.MavenUtil

val tracer by lazy { TelemetryManager.getTracer(Scope(MavenUtil.MAVEN_NAME)) }
