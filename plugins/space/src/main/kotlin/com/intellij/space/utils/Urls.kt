// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.utils

import circlet.client.api.Navigator
import circlet.client.api.ProjectKey
import com.intellij.space.settings.SpaceSettings

object Urls {
  fun spaceProject(projectKey: ProjectKey): String = Navigator.p.project(projectKey).absoluteHref(server())

  private fun server(): String = SpaceSettings.getInstance().serverSettings.server
}