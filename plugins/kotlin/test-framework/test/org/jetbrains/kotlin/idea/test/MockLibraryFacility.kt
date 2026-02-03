// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptLibraryKind
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import java.io.File

class MockLibraryFacility(
    val sources: List<File>,
    val attachSources: Boolean = true,
    val platform: KotlinCompilerStandalone.Platform = KotlinCompilerStandalone.Platform.Jvm(),
    options: List<String> = emptyList(),
    val classpath: List<File> = emptyList(),
    val libraryName: String = MOCK_LIBRARY_NAME,
    val target: File = KotlinCompilerStandalone.defaultTargetJar(),
    val frontend: KotlinCompilerFrontend = KotlinCompilerFrontend.K1,
) {

    private val options: List<String>

    init {
        val languageVersionByCompilerOptionsIndex = options.indexOf(LANGUAGE_VERSION_PARAMETER_NAME)
        if (languageVersionByCompilerOptionsIndex != -1) {
            val languageVersion = LanguageVersion.fromVersionString(options[languageVersionByCompilerOptionsIndex + 1])
                ?: error("Invalid language version provided")
            when (frontend) {
                KotlinCompilerFrontend.K1 -> {
                    require(languageVersion < LanguageVersion.KOTLIN_2_0) {
                        "Cannot use $languageVersion with K1 compiler"
                    }
                }
                KotlinCompilerFrontend.K2 ->  {
                    require(languageVersion >= LanguageVersion.KOTLIN_2_0) {
                        "Cannot use $languageVersion with K2 compiler"
                    }
                }
            }
            this.options = options
        } else {
            val languageVersionToUse = when (frontend) {
                KotlinCompilerFrontend.K1 -> LanguageVersion.values().last { !it.usesK2 }
                KotlinCompilerFrontend.K2 -> maxOf(LanguageVersion.LATEST_STABLE, LanguageVersion.KOTLIN_2_0)
            }
            this.options = options + listOf(LANGUAGE_VERSION_PARAMETER_NAME, languageVersionToUse.versionString)
        }
    }

    constructor(
        source: File,
        attachSources: Boolean = true,
        platform: KotlinCompilerStandalone.Platform = KotlinCompilerStandalone.Platform.Jvm(),
        options: List<String> = emptyList(),
        classpath: List<File> = emptyList(),
        libraryName: String = MOCK_LIBRARY_NAME,
        target: File = KotlinCompilerStandalone.defaultTargetJar(),
        frontend: KotlinCompilerFrontend = KotlinCompilerFrontend.K1,
    ) : this(listOf(source), attachSources, platform, options, classpath, libraryName, target, frontend)

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

    enum class KotlinCompilerFrontend {
        K1, K2
    }
}

private const val LANGUAGE_VERSION_PARAMETER_NAME = "-language-version"
