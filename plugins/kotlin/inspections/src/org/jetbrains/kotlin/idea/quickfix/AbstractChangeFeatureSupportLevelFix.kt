// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixFactory
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.buildSystemType
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.inspections.KotlinInspectionsBundle

abstract class AbstractChangeFeatureSupportLevelFix(
    element: PsiElement,
    protected val feature: LanguageFeature,
    protected val featureSupport: LanguageFeature.State,
    private val featureShortName: String = feature.presentableName
) : KotlinQuickFixAction<PsiElement>(element) {
    protected val featureSupportEnabled: Boolean
        get() = featureSupport == LanguageFeature.State.ENABLED

    final override fun getFamilyName() = KotlinInspectionsBundle.message("fix.change.feature.support.family", featureShortName)

    override fun getText(): String = getFixText(featureSupport, featureShortName)

    companion object {
        @Nls
        fun getFixText(state: LanguageFeature.State, featureShortName: String): String {
            return when (state) {
                LanguageFeature.State.ENABLED -> {
                    KotlinInspectionsBundle.message("fix.change.feature.support.enabled", featureShortName)
                }
                LanguageFeature.State.DISABLED -> {
                    KotlinInspectionsBundle.message("fix.change.feature.support.disabled", featureShortName)
                }
            }
        }
    }

    interface FeatureSupportIntentionActionsFactory : QuickFixFactory {
        fun shouldConfigureInProject(module: Module): Boolean {
            val facetSettings = KotlinFacet.get(module)?.configuration?.settings
            return (facetSettings == null || facetSettings.useProjectSettings) &&
                   module.buildSystemType == BuildSystemType.JPS
        }
    }
}
