// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.telemetry

import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.CoroutineTracerShim
import com.intellij.openapi.project.Project
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.exporters.OpenTelemetryRawTraceExporter
import com.intellij.platform.diagnostic.telemetry.impl.computeSpanId
import com.intellij.platform.diagnostic.telemetry.impl.computeTraceId
import com.intellij.platform.diagnostic.telemetry.impl.getOtlpEndPoint
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.net.URI

val tracer by lazy { TelemetryManager.getSimpleTracer(Scope(MavenUtil.MAVEN_NAME)) }

internal data class TelemetryIds(@JvmField val traceId: String?, @JvmField val spanId: String?)

private val emptyIds = TelemetryIds(traceId = null, spanId = null)

internal suspend fun getCurrentTelemetryIds(): TelemetryIds {
  val activity = CoroutineTracerShim.coroutineTracer.getTraceActivity()
  if (null == activity) return emptyIds
  if (activity !is ActivityImpl) {
    MavenLog.LOG.error("ActivityImpl expected")
    return emptyIds
  }
  val traceId = bytesToHex(computeTraceId(activity))
  val spanId = bytesToHex(computeSpanId(activity))

  return TelemetryIds(traceId, spanId)
}

private fun bytesToHex(bytes: ByteArray): String {
  val hexString = StringBuilder()
  for (b in bytes) {
    val hex = Integer.toHexString(0xff and b.toInt())
    if (hex.length == 1) {
      hexString.append('0')
    }
    hexString.append(hex)
  }
  return hexString.toString()
}

internal fun scheduleExportTelemetryTrace(project: Project, binaryTrace: ByteArray) {
  if (binaryTrace.isEmpty()) return

  val telemetryHost = getOtlpEndPoint()
  if (null == telemetryHost) return

  val cs = MavenCoroutineScopeProvider.getCoroutineScope(project)
  cs.launch {
    OpenTelemetryRawTraceExporter.sendProtobuf(URI.create(telemetryHost), binaryTrace)
  }
}