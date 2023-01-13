// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptLibraryKind
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import java.io.File

data class MockLibraryFacility(
    val sources: List<File>,
    val attachSources: Boolean = true,
    val platform: KotlinCompilerStandalone.Platform = KotlinCompilerStandalone.Platform.Jvm(),
    val options: List<String> = emptyList(),
    val classpath: List<File> = emptyList(),
    val libraryName: String = MOCK_LIBRARY_NAME,
    val target: File = KotlinCompilerStandalone.defaultTargetJar(),
) {
    constructor(
        source: File,
        attachSources: Boolean = true,
        platform: KotlinCompilerStandalone.Platform = KotlinCompilerStandalone.Platform.Jvm(),
        options: List<String> = emptyList(),
        classpath: List<File> = emptyList(),
        libraryName: String = MOCK_LIBRARY_NAME,
        target: File = KotlinCompilerStandalone.defaultTargetJar(),
    ) : this(listOf(source), attachSources, platform, options, classpath, libraryName, target)

    companion object {
        const val MOCK_LIBRARY_NAME = "kotlinMockLibrary"

        fun tearDown(module: Module, libraryName: String) {
            ConfigLibraryUtil.removeLibrary(module, libraryName)
        }
    }

    fun setUp(module: Module) {
        val libraryJar = KotlinCompilerStandalone(
            sources,
            platform = platform,
            options = options,
            classpath = classpath,
            target = target,
        ).compile()

        val kind = if (platform is KotlinCompilerStandalone.Platform.JavaScript) KotlinJavaScriptLibraryKind else null
        ConfigLibraryUtil.addLibrary(module, libraryName, kind) {
            addRoot(libraryJar, OrderRootType.CLASSES)

            if (attachSources) {
                for (source in sources) {
                    addRoot(source, OrderRootType.SOURCES)
                }
            }
        }
    }

    fun tearDown(module: Module) = tearDown(module, libraryName)

    val asKotlinLightProjectDescriptor: KotlinLightProjectDescriptor
        get() = object : KotlinLightProjectDescriptor() {
            override fun configureModule(module: Module, model: ModifiableRootModel) = this@MockLibraryFacility.setUp(module)

            override fun getSdk(): Sdk = if (this@MockLibraryFacility.platform is KotlinCompilerStandalone.Platform.JavaScript)
                KotlinSdkType.INSTANCE.createSdkWithUniqueName(emptyList())
            else
                IdeaTestUtil.getMockJdk18()
        }
}
