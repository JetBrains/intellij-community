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

import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.intellij.codeInsight.lookup.Classifier
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.tracker.LookupElementTracking
import com.intellij.completion.tracker.StagePosition
import com.intellij.openapi.util.Pair
import com.intellij.util.ProcessingContext

class ElementPositionHistoryEmptyClassifier(
        private val lookup: LookupImpl,
        next: Classifier<LookupElement>?
): Classifier<LookupElement>(next, "elementPositionHistory") {

    override fun classify(iterable: MutableIterable<LookupElement>,
                          context: ProcessingContext): MutableIterable<LookupElement> {
        return iterable
    }

    override fun getSortingWeights(iterable: MutableIterable<LookupElement>,
                                   context: ProcessingContext): List<Pair<LookupElement, Any>> {
        return iterable.map {
            val history = LookupElementTracking.getInstance().positionsHistory(lookup, it)
            Pair.create(it, PositionHistoryPresentation(history) as Any)
        }
    }

}

class PositionHistoryPresentation(private val history: List<StagePosition>) {

    companion object {
        private val gson = Gson()
        private val token = object : TypeToken<List<StagePosition>>() {}
    }

    override fun toString() = gson.toJson(history, token.type)!!

}