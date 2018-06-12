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

package com.intellij.completion.contributors

import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.completion.enhancer.CompletionContributors


object CompletionContributorUtils {

    fun add(contributorEP: CompletionContributorEP) {
        val extensionPoint = CompletionContributors.extensionPoint()
        extensionPoint.registerExtension(contributorEP)
    }

    fun remove(contributorEP: CompletionContributorEP) {
        val extensionPoint = CompletionContributors.extensionPoint()
        extensionPoint.unregisterExtension(contributorEP)
    }

    fun first(): CompletionContributorEP {
        val extensionPoint = CompletionContributors.extensionPoint()
        return extensionPoint.extensions.first()
    }

    fun removeFirst() {
        val point = CompletionContributors.extensionPoint()
        val first = point.extensions.first()
        point.unregisterExtension(first)
    }

}

