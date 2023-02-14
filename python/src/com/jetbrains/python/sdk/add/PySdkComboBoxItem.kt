// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

import com.intellij.openapi.projectRoots.Sdk

sealed class PySdkComboBoxItem

class NewPySdkComboBoxItem(val title: String) : PySdkComboBoxItem()

class ExistingPySdkComboBoxItem(val sdk: Sdk) : PySdkComboBoxItem()

fun Sdk.asComboBoxItem() = ExistingPySdkComboBoxItem(this)