// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.telemetry

import com.intellij.openapi.project.Project
import com.intellij.platform.diagnostic.telemetry.OtlpConfiguration.getTraceEndpoint
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.exporters.OpenTelemetryRawTraceExporter
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import org.jetbrains.idea.maven.utils.MavenUtil
import java.net.URI

val tracer by lazy { TelemetryManager.getTracer(Scope(MavenUtil.MAVEN_NAME)) }

internal fun scheduleExportTelemetryTrace(project: Project, binaryTrace: ByteArray) {
  if (binaryTrace.isEmpty()) return

  val telemetryHost = getTraceEndpoint()
  if (null == telemetryHost) return

  val cs = MavenCoroutineScopeProvider.getCoroutineScope(project)
  cs.launch {
    OpenTelemetryRawTraceExporter.sendProtobuf(URI.create(telemetryHost), binaryTrace)
  }
}