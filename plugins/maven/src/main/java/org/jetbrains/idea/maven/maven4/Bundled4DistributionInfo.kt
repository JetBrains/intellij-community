// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.maven4

import org.jetbrains.idea.maven.maven3.BundledDistributionInfo

class Bundled4DistributionInfo(version: String?) : BundledDistributionInfo(version ?: "4")