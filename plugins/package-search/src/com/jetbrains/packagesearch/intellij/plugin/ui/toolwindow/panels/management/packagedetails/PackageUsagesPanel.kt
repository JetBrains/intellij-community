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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails

import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.pom.Navigatable
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.fus.FUSGroupIds
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.util.HtmlEditorPane
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import javax.swing.BoxLayout

internal class PackageUsagesPanel : HtmlEditorPane() {

    private val linkActionsMap = mutableMapOf<String, Navigatable?>()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = emptyBorder(top = 8)
        background = PackageSearchUI.Colors.panelBackground
    }

    override fun onLinkClicked(anchor: String) {
        val navigatable = linkActionsMap[anchor] ?: return
        if (!navigatable.canNavigate()) return
        navigatable.navigate(true)
        PackageSearchEventsLogger.logDetailsLinkClick(FUSGroupIds.DetailsLinkTypes.PackageUsages)
    }

    fun display(packageModel: PackageModel.Installed) {
        clear()

        val chunks = mutableListOf<HtmlChunk>()
        chunks += HtmlChunk.p().addText(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.usages.caption"))
        chunks += HtmlChunk.ul().children(
            packageModel.usageInfo.mapIndexed { index, usageInfo ->
                val anchor = "#$index"

                usageInfo.declarationIndexInBuildFile
                    ?.let { usageInfo.projectModule.getBuildFileNavigatableAtOffset(it.wholeDeclarationStartIndex) }
                    ?.let { linkActionsMap[anchor] = it }

                HtmlChunk.li().child(
                    HtmlChunk.link(anchor, usageInfo.projectModule.name)
                )
            }
        )
        setBody(chunks)
    }

    fun clear() {
        clearBody()
        linkActionsMap.clear()
    }
}
