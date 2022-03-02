// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.composeProjectWizard

import com.intellij.ide.starters.local.StarterModuleBuilder.Companion.setupTestModule
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_11
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase4
import com.intellij.ide.starters.shared.KOTLIN_STARTER_LANGUAGE
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Verifier

//JAVA 11 is used since it is the oldest JDK supported by IDEA
class ComposeModuleBuilderTest : LightJavaCodeInsightFixtureTestCase4(JAVA_11) {
    @Test
    fun testDesktopProject() {
        init(ComposePWInitialStep.ComposeConfigurationType.SINGLE_PLATFORM, ComposePWInitialStep.ComposePlatform.DESKTOP)
        fixture.testDataPath += "/plugins/kotlin/project-wizard-compose/testData/etalons/desktop/"

        fixture.checkResultEx("src/jvmMain/kotlin/Main.kt")
    }

    @Test
    fun testWebProject() {
        init(ComposePWInitialStep.ComposeConfigurationType.SINGLE_PLATFORM, ComposePWInitialStep.ComposePlatform.WEB)
        fixture.testDataPath += "/plugins/kotlin/project-wizard-compose/testData/etalons/web/"

        fixture.checkResultEx("src/jsMain/kotlin/Main.kt")
        fixture.checkResultEx("src/jsMain/resources/index.html")
    }

    @Test
    fun testMppProject() {
        init(ComposePWInitialStep.ComposeConfigurationType.MULTI_PLATFORM, ComposePWInitialStep.ComposePlatform.DESKTOP)
        fixture.testDataPath += "/plugins/kotlin/project-wizard-compose/testData/etalons/mpp/"

        listOf("android/build.gradle.kts",
               "android/src/main/AndroidManifest.xml",
               "android/src/main/java/com/example/android/MainActivity.kt",
               "common/build.gradle.kts",
               "common/src/androidMain/kotlin/com/example/common/platform.kt",
               "common/src/androidMain/AndroidManifest.xml",
               "common/src/commonMain/kotlin/com/example/common/platform.kt",
               "common/src/commonMain/kotlin/com/example/common/App.kt",
               "common/src/desktopMain/kotlin/com/example/common/platform.kt",
               "common/src/desktopMain/kotlin/com/example/common/DesktopApp.kt",
               "desktop/build.gradle.kts",
               "desktop/src/jvmMain/kotlin/Main.kt"
        ).forEach {fixture.checkResultEx(it)}
    }


    fun commonTestPart() {
        listOf("gradle.properties",
               "gradle/wrapper/gradle-wrapper.properties",
               "settings.gradle.kts",
               "build.gradle.kts"
        ).forEach {fixture.checkResultEx(it)}
    }

    fun init(configType: ComposePWInitialStep.ComposeConfigurationType, platform : ComposePWInitialStep.ComposePlatform ) {
        ComposeModuleBuilder().setupTestModule(fixture.module) {
            language = KOTLIN_STARTER_LANGUAGE
            isCreatingNewProject = true
            putUserData(ComposeModuleBuilder.COMPOSE_CONFIG_TYPE_KEY, configType)
            putUserData(ComposeModuleBuilder.COMPOSE_PLATFORM_KEY, platform)
        }
    }

    private fun JavaCodeInsightTestFixture.checkResultEx(path : String) {
        checkResultByFile(path, path, true)
    }

    //this check is called as a part of each test rule (in the end)
    @get:Rule
    val verifier = object: Verifier() {
        override fun verify() {
            commonTestPart()
        }
    }
}