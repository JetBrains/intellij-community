package com.intellij.ide.starter.models

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class IDEStartupReports(startupReportsDir: Path) {
  val time: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
  val statsJSON: Path = startupReportsDir.resolve("startup-stats-$time.json")

  val statsObject: ObjectNode
    get() = ObjectMapper().readTree(statsJSON.toFile()) as ObjectNode

  override fun toString(): String {
    return "IDEStartupReports(statsJSON=$statsJSON)"
  }
}