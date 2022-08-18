package com.intellij.ide.starter.models

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.file.Path

class IDEStartupReports(startupReportsDir: Path) {
  val statsJSON: Path = startupReportsDir.resolve("startup-stats.json")

  val statsObject
    get() = ObjectMapper().readTree(statsJSON.toFile()) as ObjectNode

  override fun toString(): String {
    return "IDEStartupReports(statsJSON=$statsJSON)"
  }
}