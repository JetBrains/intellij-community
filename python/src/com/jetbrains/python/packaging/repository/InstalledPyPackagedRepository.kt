// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.jetbrains.python.PyBundle

class InstalledPyPackagedRepository : PyPackageRepository(PyBundle.message("python.toolwindow.packages.installed.label"), null, null)