// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.*
import com.intellij.codeInsight.lookup.LookupElement

class PyAdapterElementFeatureProvider(private val delegate: ElementFeatureProvider) : ElementFeatureProvider {
  val features: MutableMap<LookupElement, Map<String, MLFeatureValue>> = hashMapOf()

  override fun getName(): String = delegate.name

  override fun calculateFeatures(element: LookupElement,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): MutableMap<String, MLFeatureValue> {
    val calculatedFeatures = delegate.calculateFeatures(element, location, contextFeatures)
    features[element] = calculatedFeatures
    return calculatedFeatures
  }
}

class PyAdapterContextFeatureProvider(private val delegate: ContextFeatureProvider): ContextFeatureProvider {
  val features: MutableMap<String, MLFeatureValue> = hashMapOf()

  override fun getName(): String = delegate.name

  override fun calculateFeatures(environment: CompletionEnvironment): Map<String, MLFeatureValue> {
    val calculatedFeatures = delegate.calculateFeatures(environment)
    features.putAll(calculatedFeatures)
    return calculatedFeatures
  }
}
