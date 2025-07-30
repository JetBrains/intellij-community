// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.stubs.checkers

import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.NormalizedPythonPackageName

internal data class PyPackageStubLink(val packageName: NormalizedPythonPackageName, val stubRequirement: PyRequirement)