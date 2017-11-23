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
  }

  fun getBoolean(factorId: String): Boolean?
  fun getDouble(factorId: String): Double?
  fun getString(factorId: String): String?

  fun setBoolean(factorId: String, value: Boolean)
  fun setDouble(factorId: String, value: Double)
  fun setString(factorId: String, value: String)
}