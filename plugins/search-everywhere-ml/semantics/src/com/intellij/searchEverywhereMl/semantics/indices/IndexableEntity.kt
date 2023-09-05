package com.intellij.searchEverywhereMl.semantics.indices

interface IndexableEntity {
  val id: String
  val indexableRepresentation: String
}