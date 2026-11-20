// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session

import org.assertj.core.api.Assertions.assertThat
import java.text.Normalizer

internal fun createTerminalLogWorkload(name: String): TerminalLogWorkload {
  val unicode = name.startsWith("utf8-")
  val longLines = name == "utf8-long-lines-1mib"
  val payloadBytes = if (name == "ascii-5mib") 5 * 1_048_576 else 1_048_576
  val recordBytes = if (longLines) 4096 else if (unicode) 512 else 256
  val part = if (unicode) "é ä x\u0301 a\u20DD 👩🏽‍💻 👨‍👩‍👧‍👦 🇦🇲 1️⃣ क्‍ष 你好世界"
  else "abcdefghijklmnopqrstuvwxyz-0123456789"
  val body = List(if (longLines) 24 else 3) { part }.joinToString(" ")
  val prefix = "2026-10-09 12:34:56,000 [000000] INFO request=000000 state=RUNNING payload="
  val padding = ".".repeat(recordBytes - prefix.length - body.toByteArray(Charsets.UTF_8).size - 2)
  val records = payloadBytes / recordBytes
  val payload = buildString(payloadBytes) {
    repeat(records) { record ->
      val id = record.toString().padStart(6, '0')
      append("2026-10-09 12:34:56,000 [").append(id).append("] INFO request=").append(id)
      append(" state=RUNNING payload=").append(body).append(padding).append("\r\n")
    }
  }
  assertThat(Normalizer.isNormalized(payload, Normalizer.Form.NFC)).isTrue()
  assertThat(payload.toByteArray(Charsets.UTF_8)).hasSize(payloadBytes)
  return TerminalLogWorkload(name, payload, payloadBytes, records, recordBytes)
}

internal class TerminalLogWorkload(val name: String, val payload: String, val payloadBytes: Int, val records: Int, val recordBytes: Int)
