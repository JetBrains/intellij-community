// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.composeProjectWizard

import com.intellij.ide.starters.local.StarterModuleBuilder.Companion.setupTestModule
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_11
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase4
import com.intellij.ide.starters.shared.KOTLIN_STARTER_LANGUAGE
import org.junit.Test

class ComposeModuleBuilderTest : LightJavaCodeInsightFixtureTestCase4(JAVA_11) {
    @Test
    fun testDesktopProject() {
        ComposeModuleBuilder().setupTestModule(fixture.module) {
            language = KOTLIN_STARTER_LANGUAGE
            isCreatingNewProject = true
            putUserData(ComposeModuleBuilder.COMPOSE_CONFIG_TYPE_KEY, ComposePWInitialStep.ComposeConfigurationType.SINGLE_PLATFORM)
            putUserData(ComposeModuleBuilder.COMPOSE_PLATFORM_KEY, ComposePWInitialStep.ComposePlatform.DESKTOP)
        }

        fixture.testDataPath = fixture.testDataPath + "/plugins/kotlin/project-wizard-compose/testData/etalons/desktop/";
        commonTestPart()
        fixture.checkResultByFile("src/jvmMain/kotlin/Main.kt", "src/jvmMain/kotlin/Main.kt", true)
    }

    @Test
    fun testWebProject() {
        ComposeModuleBuilder().setupTestModule(fixture.module) {
            language = KOTLIN_STARTER_LANGUAGE
            isCreatingNewProject = true
            putUserData(ComposeModuleBuilder.COMPOSE_CONFIG_TYPE_KEY, ComposePWInitialStep.ComposeConfigurationType.SINGLE_PLATFORM)
            putUserData(ComposeModuleBuilder.COMPOSE_PLATFORM_KEY, ComposePWInitialStep.ComposePlatform.WEB)
        }

        fixture.testDataPath = fixture.testDataPath + "/plugins/kotlin/project-wizard-compose/testData/etalons/web/";
        commonTestPart()
        fixture.checkResultByFile("src/jsMain/kotlin/Main.kt", "src/jsMain/kotlin/Main.kt", true)
        fixture.checkResultByFile("src/jsMain/resources/index.html", "src/jsMain/resources/index.html", true)
    }

    @Test
    fun testMppProject() {
        ComposeModuleBuilder().setupTestModule(fixture.module) {
            language = KOTLIN_STARTER_LANGUAGE
            isCreatingNewProject = true
            putUserData(ComposeModuleBuilder.COMPOSE_CONFIG_TYPE_KEY, ComposePWInitialStep.ComposeConfigurationType.MULTI_PLATFORM)
            putUserData(ComposeModuleBuilder.COMPOSE_PLATFORM_KEY, ComposePWInitialStep.ComposePlatform.DESKTOP) //actially doesn't matter
        }

        fixture.testDataPath = fixture.testDataPath + "/plugins/kotlin/project-wizard-compose/testData/etalons/mpp/";
        commonTestPart()
        fixture.checkResultByFile("android/build.gradle.kts", "android/build.gradle.kts", true)
        fixture.checkResultByFile("android/src/main/AndroidManifest.xml", "android/src/main/AndroidManifest.xml", true)
        fixture.checkResultByFile("android/src/main/java/com/example/android/MainActivity.kt", "android/src/main/java/com/example/android/MainActivity.kt", true)
        fixture.checkResultByFile("common/build.gradle.kts", "common/build.gradle.kts", true)
        fixture.checkResultByFile("common/src/androidMain/kotlin/com/example/common/platform.kt", "common/src/androidMain/kotlin/com/example/common/platform.kt", true)
        fixture.checkResultByFile("common/src/androidMain/AndroidManifest.xml", "common/src/androidMain/AndroidManifest.xml", true)
        fixture.checkResultByFile("common/src/commonMain/kotlin/com/example/common/platform.kt", "common/src/commonMain/kotlin/com/example/common/platform.kt", true)
        fixture.checkResultByFile("common/src/commonMain/kotlin/com/example/common/App.kt", "common/src/commonMain/kotlin/com/example/common/App.kt", true)
        fixture.checkResultByFile("common/src/desktopMain/kotlin/com/example/common/platform.kt", "common/src/desktopMain/kotlin/com/example/common/platform.kt", true)
        fixture.checkResultByFile("common/src/desktopMain/kotlin/com/example/common/DesktopApp.kt", "common/src/desktopMain/kotlin/com/example/common/DesktopApp.kt", true)
        fixture.checkResultByFile("desktop/build.gradle.kts", "desktop/build.gradle.kts", true)
        fixture.checkResultByFile("desktop/src/jvmMain/kotlin/Main.kt", "desktop/src/jvmMain/kotlin/Main.kt", true)
    }


    fun commonTestPart() {
        fixture.checkResultByFile("gradle.properties", "gradle.properties", true)
        fixture.checkResultByFile("gradle/wrapper/gradle-wrapper.properties", "gradle/wrapper/gradle-wrapper.properties", true)
        fixture.checkResultByFile("settings.gradle.kts", "settings.gradle.kts", true)
        fixture.checkResultByFile("build.gradle.kts", "build.gradle.kts", true)
    }
}