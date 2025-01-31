// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty

interface KotlinOnboardingProjectWizardData {
    val addSampleCodeProperty: GraphProperty<Boolean>

    var addSampleCode: Boolean

    @Deprecated("Use addSampleCodeProperty instead")
    val generateOnboardingTipsProperty: ObservableMutableProperty<Boolean>
        get() = addSampleCodeProperty

    @Deprecated("Use addSampleCode instead")
    val generateOnboardingTips: Boolean
        get() = addSampleCode
}