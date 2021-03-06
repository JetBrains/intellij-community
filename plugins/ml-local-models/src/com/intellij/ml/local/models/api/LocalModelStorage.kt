package com.intellij.ml.local.models.api

interface LocalModelStorage {
  fun version(): Int
  fun name(): String
  fun isValid(): Boolean
  fun isEmpty(): Boolean
  fun setValid(isValid: Boolean)
}