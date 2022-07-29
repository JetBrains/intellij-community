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

package com.jetbrains.packagesearch.intellij.plugin.ui.components

import com.intellij.ui.HyperlinkLabel
import org.jetbrains.annotations.Nls

internal class BrowsableLinkLabel : HyperlinkLabel() {

    var urlClickedListener: (() -> Unit)? = null

    init {
        addHyperlinkListener { urlClickedListener?.invoke() }
    }

    var url: String? = null
        set(value) {
            if (value.isBrowsableUrl) {
                isVisible = true
                setHyperlinkTarget(value)
                icon = null // We need to reset it every time — calling setHyperlinkTarget() sets it, because reasons
            } else {
                isVisible = false
            }
            field = value
        }

    private val String?.isBrowsableUrl: Boolean
        get() {
            if (isNullOrBlank()) return false
            val normalizedUrl = trim()
            return normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://")
        }

    fun setDisplayText(@Nls text: String?) {
        if (text != null) {
            setHyperlinkText(text)
        } else {
            super.setText(null)
        }
    }
}
