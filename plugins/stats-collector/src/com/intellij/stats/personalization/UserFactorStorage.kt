package com.intellij.stats.personalization

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.stats.personalization.impl.ApplicationUserFactorStorage
import com.intellij.stats.personalization.impl.ProjectUserFactorStorage

/**
 * @author Vitaliy.Bibaev
 */
interface UserFactorStorage {
  companion object {
    fun getInstance(): UserFactorStorage =
        ApplicationManager.getApplication().getComponent(ApplicationUserFactorStorage::class.java)

    fun getInstance(project: Project): UserFactorStorage = project.getComponent(ProjectUserFactorStorage::class.java)

    fun <U : FactorUpdater> applyOnBoth(project: Project, description: UserFactorDescription<U, *>, updater: (U) -> Unit) {
      updater(getInstance().getFactorUpdater(description))
      updater(getInstance(project).getFactorUpdater(description))
    }
  }

  fun <U : FactorUpdater> getFactorUpdater(description: UserFactorDescription<U, *>): U
  fun <R : FactorReader> getFactorReader(description: UserFactorDescription<*, R>): R
}