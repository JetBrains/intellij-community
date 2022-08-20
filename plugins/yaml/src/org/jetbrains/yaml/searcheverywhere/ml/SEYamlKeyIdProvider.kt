// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.ml.id.ElementKeyForIdProvider
import org.jetbrains.yaml.navigation.YAMLKeyNavigationItem

private class SEYamlKeyIdProvider : ElementKeyForIdProvider() {
  override fun getKey(element: Any): Any? {
    return element as? YAMLKeyNavigationItem
  }
}