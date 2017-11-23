package com.intellij.stats.personalization

import com.intellij.openapi.project.Project

/**
 * @author Vitaliy.Bibaev
 */
interface UserFactorsManager {
  companion object {
    fun getInstance(project: Project): UserFactorsManager = project.getComponent(UserFactorsManager::class.java)
  }

  fun getAllFactorIds(): List<String>

  fun getFactor(id: String): UserFactor
}
