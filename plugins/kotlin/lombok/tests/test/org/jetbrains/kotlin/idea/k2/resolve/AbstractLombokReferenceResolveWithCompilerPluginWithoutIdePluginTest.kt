// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.resolve

import com.intellij.psi.augment.PsiAugmentProvider
import com.intellij.testFramework.ExtensionTestUtil
import de.plushnikov.intellij.plugin.provider.LombokAugmentProvider
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveWithCompilerPluginsInSourceTest

abstract class AbstractLombokReferenceResolveWithCompilerPluginWithoutIdePluginTest : AbstractReferenceResolveWithCompilerPluginsInSourceTest() {

    override val compilerPlugins: List<CompilerPluginConfiguration> = listOf(
        CompilerPluginConfiguration(
            name = "LOMBOK",
            registrarClassName = "org.jetbrains.kotlin.lombok.LombokComponentRegistrar",
            libraryCoordinates = "org.projectlombok:lombok:1.18.26",
        ),
    )

    override fun setUp() {
        super.setUp()
        // The IDE provider generates Java PSI members that can hide failures in compiler plugin reference resolution.
        // Mask only this provider to keep the Lombok plugin and its index loaded. The test disposable restores it.
        val point = PsiAugmentProvider.EP_NAME
        ExtensionTestUtil.maskExtensions(
            point,
            point.extensionList.filterNot { it is LombokAugmentProvider },
            testRootDisposable,
        )
    }
}
