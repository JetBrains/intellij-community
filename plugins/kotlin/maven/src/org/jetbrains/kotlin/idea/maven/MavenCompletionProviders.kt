// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.idea.maven.plugins.api.MavenFixedValueReferenceProvider
import org.jetbrains.kotlin.cli.common.arguments.DefaultValues
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.isStableOrReadyForPreview
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode

class MavenLanguageVersionsCompletionProvider : MavenFixedValueReferenceProvider(
    LanguageVersion.values().filter { it.isStableOrReadyForPreview() || isApplicationInternalMode() }.map { it.versionString }
        .toTypedArray()
)

class MavenApiVersionsCompletionProvider : MavenFixedValueReferenceProvider(
    LanguageVersion.values().filter { it.isStableOrReadyForPreview() || isApplicationInternalMode() }.map { it.versionString }
        .toTypedArray()
)

class MavenJvmTargetsCompletionProvider : MavenFixedValueReferenceProvider(
    JvmTarget.supportedValues().map(JvmTarget::description).toTypedArray()
)

class MavenJsModuleKindsCompletionProvider : MavenFixedValueReferenceProvider(
    DefaultValues.JsModuleKinds.possibleValues!!.map(StringUtil::unquoteString).toTypedArray()
)

class MavenJsMainCallCompletionProvider : MavenFixedValueReferenceProvider(
    DefaultValues.JsMain.possibleValues!!.map(StringUtil::unquoteString).toTypedArray()
)
