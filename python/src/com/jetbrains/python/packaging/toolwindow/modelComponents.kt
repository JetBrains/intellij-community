// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.packaging.PyPackage

sealed class DisplayablePackage(@NlsSafe val name: String)
class InstalledPackage(val instance: PyPackage) : DisplayablePackage(instance.name)
class InstallablePackage(name: String) : DisplayablePackage(name)
class PackageInfo(val documentationUrl: String?, @NlsSafe val description: String, val availableVersions: List<String>)