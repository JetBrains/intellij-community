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

package com.intellij.completion.enhancer

import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator

class FirstContributorPreloader : PreloadingActivity() {
    private companion object {
        val LOG = Logger.getInstance(FirstContributorPreloader::class.java)
    }


    override fun preload(indicator: ProgressIndicator) {
        val id = PluginId.findId("com.intellij.stats.completion")
        val descriptor = PluginManager.getPlugin(id)

        if (descriptor == null) {
            LOG.error("Plugin descriptor not found")
        } else {
//            addInvocationCountContributor(descriptor) // disabled for a while
        }
    }

    @Suppress("unused")
    private fun addInvocationCountContributor(descriptor: IdeaPluginDescriptor) {
        addContributor(descriptor, InvocationCountEnhancingContributor::class.java.name)
    }

    private fun addContributor(descriptor: IdeaPluginDescriptor, implClass: String) {
        CompletionContributorEP().apply {
            implementationClass = implClass
            language = "any"
            pluginDescriptor = descriptor
        }.let {
            CompletionContributors.addFirst(it)
        }
    }

}