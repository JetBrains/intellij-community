// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import java.net.URI

data class MavenWrapperConfiguration(
  val distributionURI: String,
  val distributionBase: String,
  val wrapperUrl: String,
  val zipBase: String,
  val zipPath: String
)