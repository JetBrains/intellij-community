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

import com.intellij.util.ui.FormBuilder

/**
 * Provides an entrypoint to add custom UI to the settings panel under Package Search entry.
 */
interface ConfigurableContributorDriver {

    /**
     * Invoked with a [builder] to use to build the interface. Use [builder] to add custom UI to the settings panel.
     */
    fun contributeUserInterface(builder: FormBuilder)

    /**
     * Checks if the users has modified some settings.
     */
    fun isModified(): Boolean

    /**
     * Resets the settings to a state before the user has modified any of them.
     */
    fun reset()

    /**
     * Restores defaults settings.
     */
    fun restoreDefaults()

    /**
     * Applies all changes made by the user.
     */
    fun apply()
}
