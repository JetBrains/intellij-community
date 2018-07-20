/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.stats.personalization

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

/**
 * @author Vitaliy.Bibaev
 */
interface UserFactorsManager {
  companion object {
    val USER_FACTORS_KEY: Key<Map<String, String?>> = Key.create<Map<String, String?>>("com.intellij.stats.personalization.userFactors")
    fun getInstance(project: Project): UserFactorsManager = project.getComponent(UserFactorsManager::class.java)
  }

  fun getAllFactorIds(): List<String>

  fun getAllFactors(): List<UserFactor>

  fun getFactor(id: String): UserFactor
}
