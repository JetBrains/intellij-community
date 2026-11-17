// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.composeProjectWizard

import com.intellij.ide.starters.local.StarterModuleBuilder.Companion.setupTestModule
import com.intellij.ide.starters.shared.KOTLIN_STARTER_LANGUAGE
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_11
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

//JAVA 11 is used since it is the oldest JDK supported by IDEA
@TestApplication
class ComposeModuleBuilderTest {
    companion object {
        private val projectFixture = lightJavaProjectFixture(JAVA_11)
    }

    private val fixture by lightJavaCodeInsightFixture(projectFixture)
    @Test
    fun testDesktopProject() {
        init()
        fixture.testDataPath += "/plugins/kotlin/project-wizard/compose/testData/etalons/desktop/"
        fixture.checkResultEx("src/main/kotlin/Main.kt")
    }

    fun commonTestPart() {
        listOf("gradle.properties",
               "gradle/wrapper/gradle-wrapper.properties",
               "settings.gradle.kts",
               "build.gradle.kts"
        ).forEach {fixture.checkResultEx(it)}
    }

    fun init() {
        ComposeModuleBuilder().setupTestModule(fixture.module) {
            language = KOTLIN_STARTER_LANGUAGE
            isCreatingNewProject = true
        }
    }

    private fun JavaCodeInsightTestFixture.checkResultEx(path : String) {
        checkResultByFile(path, path, true)
    }

    //this check is called after each test
    @AfterEach
    fun verify() {
        commonTestPart()
    }
}