package com.intellij.ml.local.models.api

interface LocalModel {
  val id: String
  fun readyToUse(): Boolean
}