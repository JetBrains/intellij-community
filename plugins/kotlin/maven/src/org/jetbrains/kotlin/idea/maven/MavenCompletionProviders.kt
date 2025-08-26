// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven

import org.jetbrains.idea.maven.plugins.api.MavenFixedValueReferenceProvider
import org.jetbrains.kotlin.cli.common.arguments.K2JsArgumentConstants
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.isStableOrReadyForPreview
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode

class MavenLanguageVersionsCompletionProvider : MavenFixedValueReferenceProvider(
    LanguageVersion.entries.filter { it.isStableOrReadyForPreview() || isApplicationInternalMode() }.map { it.versionString }
        .toTypedArray()
)

class MavenApiVersionsCompletionProvider : MavenFixedValueReferenceProvider(
    LanguageVersion.entries.filter { it.isStableOrReadyForPreview() || isApplicationInternalMode() }.map { it.versionString }
        .toTypedArray()
)

class MavenJvmTargetsCompletionProvider : MavenFixedValueReferenceProvider(
    JvmTarget.supportedValues().map(JvmTarget::description).toTypedArray()
)

class MavenJsModuleKindsCompletionProvider : MavenFixedValueReferenceProvider(
    arrayOf(
        K2JsArgumentConstants.MODULE_PLAIN,
        K2JsArgumentConstants.MODULE_AMD,
        K2JsArgumentConstants.MODULE_COMMONJS,
        K2JsArgumentConstants.MODULE_UMD
    )
)

class MavenJsMainCallCompletionProvider : MavenFixedValueReferenceProvider(
    arrayOf(K2JsArgumentConstants.CALL, K2JsArgumentConstants.NO_CALL)
)
