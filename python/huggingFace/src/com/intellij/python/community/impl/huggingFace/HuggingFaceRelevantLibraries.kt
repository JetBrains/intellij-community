// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace

object HuggingFaceRelevantLibraries {
  val relevantLibraries = setOf(
    "diffusers", "transformers", "allennlp", "spacy",
    "asteroid", "flair", "keras", "sentence-transformers",
    "stable-baselines3", "adapters", "huggingface_hub"
  )
}
