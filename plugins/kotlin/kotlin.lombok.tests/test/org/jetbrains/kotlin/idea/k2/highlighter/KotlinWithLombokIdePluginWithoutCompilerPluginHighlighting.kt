// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighter

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import de.plushnikov.intellij.plugin.LombokTestUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

internal class KotlinWithLombokIdePluginWithoutCompilerPluginHighlighting : JavaCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    private val sdk: Sdk
        get() = LombokTestUtil.LOMBOK_JAVA_1_8_DESCRIPTOR.sdk ?: error("Lombok SDK is not found")

    override fun setUp() {
        setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
        LombokTestUtil.LOMBOK_JAVA_1_8_DESCRIPTOR.registerSdk(testRootDisposable)
    }

    fun `test lombok builder methods`() {
        val javaWithLombokModule = PsiTestUtil.addModule(
            project,
            JavaModuleType.getModuleType(),
            "javaWithLombokModule",
            myFixture.tempDirFixture.findOrCreateDir("javaWithLombokModule"),
        )

        @Language("JAVA")
        val javaDataClassText = """
            package lib;
            
            import lombok.Data;
            import lombok.AllArgsConstructor;
            import lombok.Builder;
            
            @Data
            @AllArgsConstructor
            @Builder
            public class UserDataClass {
                private String name;
                private int age;
            }
            """.trimIndent()

        val javaDataClass = myFixture.addFileToProject(
            "javaWithLombokModule/lib/UserDataClass.java",
            javaDataClassText,
        )

        myFixture.allowTreeAccessForFile(javaDataClass.virtualFile)

        ConfigLibraryUtil.configureSdk(javaWithLombokModule, sdk)
        ModuleRootModificationUtil.modifyModel(javaWithLombokModule) { model ->
            LombokTestUtil.addLombokDependency(model)
            true
        }

        val kotlinModule = PsiTestUtil.addModule(
            project,
            JavaModuleType.getModuleType(),
            "kotlinModule",
            myFixture.tempDirFixture.findOrCreateDir("kotlinModule"),
        )

        @Language("kotlin")
        val kotlinMainText = """
            package main
            
            import lib.UserDataClass
            
            fun useConstructor() {
                UserDataClass("hello", 10)
            }
            
            fun useProperties(user: UserDataClass) {
                println(user.name)
                println(user.age)
            }
            
            fun createBuilder(): UserDataClass {
                return UserDataClass.builder().name("hello").age(10).build()
            }
            
            fun useBuilder(userBuilder: UserDataClass.UserDataClassBuilder): UserDataClass {
                return userBuilder.name("hello").age(10).build()
            }
        """.trimIndent()

        val kotlinMainFile = myFixture.addFileToProject(
            "kotlinModule/Main.kt",
            kotlinMainText,
        )

        ConfigLibraryUtil.configureKotlinRuntimeAndSdk(kotlinModule, sdk)
        ModuleRootModificationUtil.addDependency(kotlinModule, javaWithLombokModule)

        myFixture.openFileInEditor(kotlinMainFile.virtualFile)
        myFixture.checkHighlighting()
    }

    fun `test lombok generic container with builder`() {
        val javaWithLombokModule = PsiTestUtil.addModule(
            project,
            JavaModuleType.getModuleType(),
            "javaWithLombokModule",
            myFixture.tempDirFixture.findOrCreateDir("javaWithLombokModule"),
        )
        
        @Language("JAVA")
        val javaContainerText = """
            package lib;
            
            import lombok.Getter;
            import lombok.Setter;
            import lombok.NoArgsConstructor;
            import lombok.AllArgsConstructor;
            import lombok.Builder;
            
            @Getter
            @Setter
            @Builder
            public class Container<D> {
                private D value;
            }
            """.trimIndent()
        
        val javaContainerFile = myFixture.addFileToProject(
            "javaWithLombokModule/lib/Container.java",
            javaContainerText,
        )

        myFixture.allowTreeAccessForFile(javaContainerFile.virtualFile)

        ConfigLibraryUtil.configureSdk(javaWithLombokModule, sdk)
        ModuleRootModificationUtil.modifyModel(javaWithLombokModule) { model ->
            LombokTestUtil.addLombokDependency(model)
            true
        }

        val kotlinModule = PsiTestUtil.addModule(
            project,
            JavaModuleType.getModuleType(),
            "kotlinModule",
            myFixture.tempDirFixture.findOrCreateDir("kotlinModule"),
        )

        @Language("kotlin")
        val kotlinMainText = """
            package main
            
            import lib.Container
            
            fun main() {
                val container = Container<String>()
            
                // check setter type
                container.value = ""
                // check getter type
                val value: String = container.value
            }
        """.trimIndent()

        val kotlinMainFile = myFixture.addFileToProject(
            "kotlinModule/Main.kt",
            kotlinMainText,
        )

        ConfigLibraryUtil.configureKotlinRuntimeAndSdk(kotlinModule, sdk)
        ModuleRootModificationUtil.addDependency(kotlinModule, javaWithLombokModule)

        myFixture.openFileInEditor(kotlinMainFile.virtualFile)
        myFixture.checkHighlighting()
    }
}