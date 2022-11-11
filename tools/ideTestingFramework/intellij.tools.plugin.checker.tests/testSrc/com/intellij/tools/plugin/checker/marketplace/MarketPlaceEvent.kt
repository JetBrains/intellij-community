package com.intellij.tools.plugin.checker.marketplace

// https://jetbrains.team/p/mp/documents/General/a/External-Services-Protocol
data class MarketPlaceEvent(
  val id: Int,
  val file: String,
  val productCode: String,
  val productVersion: String,
  val productLink: String,
  val productType: String?,
  val s3Path: String,
  val forced: Boolean?
)
