package com.intellij.stats.personalization

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

/**
 * @author Vitaliy.Bibaev
 */
interface UserFactorsManager {
  companion object {
    val USER_FACTORS_KEY = Key.create<Map<String, String?>>("com.intellij.stats.personalization.userFactors")
    fun getInstance(project: Project): UserFactorsManager = project.getComponent(UserFactorsManager::class.java)
  }

  fun getAllFactorIds(): List<String>

  fun getAllFactors(): List<UserFactor>

  fun getFeatureFactor(featureName: String): UserFactor.FeatureFactor?

  fun getFactor(id: String): UserFactor
}
