// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.telemetry

import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.CoroutineTracerShim
import com.intellij.openapi.project.Project
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.impl.computeSpanId
import com.intellij.platform.diagnostic.telemetry.impl.computeTraceId
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

internal val tracer by lazy { TelemetryManager.getSimpleTracer(Scope(MavenUtil.MAVEN_NAME)) }

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

private fun getOpenTelemetryAddress(): URI? {
  val property = System.getProperty("idea.diagnostic.opentelemetry.otlp")
  if (property == null) {
    return null
  }
  if (property.endsWith("/")) {
    return URI.create(property + "v1/traces")
  }
  return URI.create("$property/v1/traces")
}

internal fun scheduleExportTelemetryTrace(project: Project, binaryTrace: ByteArray) {
  if (binaryTrace.isEmpty()) return

  val telemetryHost = getOpenTelemetryAddress()
  if (null == telemetryHost) return

  val cs = MavenCoroutineScopeProvider.getCoroutineScope(project)
  cs.launch {
    try {
      HttpClient.newHttpClient()
        .send(HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofByteArray(binaryTrace))
                .uri(telemetryHost)
                .header("Content-Type", "application/x-protobuf")
                .build(),
              HttpResponse.BodyHandlers.discarding())
    }
    catch (e: java.lang.Exception) {
      MavenLog.LOG.error("Unable to upload performance traces to the OTLP server", e)
    }
  }
}