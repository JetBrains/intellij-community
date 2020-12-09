// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.notification

import com.intellij.notification.impl.NotificationIdsHolder

class SpaceNotificationIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> {
    return listOf(
      GIT_REPO_INIT_ERROR,
      PROJECT_SHARED_SUCCESSFULLY,
      SHARING_NOT_FINISHED
    )
  }

  companion object {
    const val GIT_REPO_INIT_ERROR = "space.git.repo.init.error"
    const val PROJECT_SHARED_SUCCESSFULLY = "space.project.shared.successfully"
    const val SHARING_NOT_FINISHED = "space.sharing.not.finished"
  }
}