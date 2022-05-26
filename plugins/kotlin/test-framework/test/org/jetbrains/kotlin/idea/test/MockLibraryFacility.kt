// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.kotlin.idea.framework.JSLibraryKind
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.platform.js.JsPlatform
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import java.io.File

data class MockLibraryFacility(
    val sources: List<File>,
    val attachSources: Boolean = true,
    val platform: KotlinCompilerStandalone.Platform = KotlinCompilerStandalone.Platform.Jvm(),
    val options: List<String> = emptyList(),
    val classpath: List<File> = emptyList()
) {
    constructor(
        source: File,
        attachSources: Boolean = true,
        platform: KotlinCompilerStandalone.Platform = KotlinCompilerStandalone.Platform.Jvm(),
        options: List<String> = emptyList(),
        classpath: List<File> = emptyList()
    ) : this(listOf(source), attachSources, platform, options, classpath)

    companion object {
        const val MOCK_LIBRARY_NAME = "kotlinMockLibrary"

        fun tearDown(module: Module) {
            ConfigLibraryUtil.removeLibrary(module, MOCK_LIBRARY_NAME)
        }
    }

    fun setUp(module: Module) {
        val libraryJar = KotlinCompilerStandalone(
            sources,
            platform = platform,
            options = options,
            classpath = classpath
        ).compile()

        val kind = if (platform is JsPlatform) JSLibraryKind else null
        ConfigLibraryUtil.addLibrary(module, MOCK_LIBRARY_NAME, kind) {
            addRoot(libraryJar, OrderRootType.CLASSES)

            if (attachSources) {
                for (source in sources) {
                    addRoot(source, OrderRootType.SOURCES)
                }
            }
        }
    }

    fun tearDown(module: Module) = Companion.tearDown(module)

    val asKotlinLightProjectDescriptor: KotlinLightProjectDescriptor
        get() = object : KotlinLightProjectDescriptor() {
            override fun configureModule(module: Module, model: ModifiableRootModel) = this@MockLibraryFacility.setUp(module)

            override fun getSdk(): Sdk = if (this@MockLibraryFacility.platform is JsPlatform)
                KotlinSdkType.INSTANCE.createSdkWithUniqueName(emptyList())
            else
                IdeaTestUtil.getMockJdk18()
        }
}
