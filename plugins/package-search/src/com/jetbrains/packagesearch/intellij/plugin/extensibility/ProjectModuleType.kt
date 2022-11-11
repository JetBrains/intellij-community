/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.project.Project
import javax.swing.Icon

enum class ProjectModuleTypeTerm {
    SCOPE
}

interface ProjectModuleType {

    val icon: Icon?
    val packageIcon: Icon?
    fun terminologyFor(term: ProjectModuleTypeTerm): String

    fun userDefinedScopes(project: Project): List<String>
    fun defaultScope(project: Project): String
}
