package com.intellij.tools.ide.metrics.collector.publishing

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class CIServerBuildInfo(
  val buildId: String,
  val typeId: String,
  val configName: String,
  val buildNumber: String,
  val branchName: String,
  val url: String,
  val isPersonal: Boolean,
  val timestamp: String = ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
)