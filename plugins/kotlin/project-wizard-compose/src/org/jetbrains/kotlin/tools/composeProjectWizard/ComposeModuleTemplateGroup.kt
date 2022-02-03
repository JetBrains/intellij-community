// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.composeProjectWizard

import com.intellij.icons.AllIcons
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory


internal class ComposeModuleTemplateGroup : FileTemplateGroupDescriptorFactory {
    override fun getFileTemplatesDescriptor(): FileTemplateGroupDescriptor {
        val root = FileTemplateGroupDescriptor("COMPOSE", AllIcons.Nodes.Module)

        with (root) {
            addTemplate(COMPOSE_SETTINGS_GRADLE)
            addTemplate(COMPOSE_GRADLE_PROPERTIES)
            addTemplate(COMPOSE_GRADLE_WRAPPER_PROPERTIES)
            addTemplate(COMPOSE_LOCAL_PROPERTIES)

            addTemplate(COMPOSE_DESKTOP_BUILD_GRADLE)
            addTemplate(COMPOSE_COMMON_BUILD_GRADLE)
            addTemplate(COMPOSE_MPP_BUILD_GRADLE)
            addTemplate(COMPOSE_ANDROID_BUILD_GRADLE)
            addTemplate(COMPOSE_WEB_BUILD_GRADLE)

            addTemplate(COMPOSE_DESKTOP_MAINKT)

            addTemplate(COMPOSE_ANDROID_MAINACTIVITYKT)
            addTemplate(COMPOSE_ANDROID_MANIFEST)

            addTemplate(COMPOSE_WEB_MAINKT)
            addTemplate(COMPOSE_WEB_INDEX_HTML)

            addTemplate(COMPOSE_COMMON_ANDROID_PLATFORMKT)
            addTemplate(COMPOSE_COMMON_ANDROID_MANIFEST)

            addTemplate(COMPOSE_COMMON_COMMON_APPKT)
            addTemplate(COMPOSE_COMMON_COMMON_PLATFORMKT)

            addTemplate(COMPOSE_COMMON_DESKTOP_APPKT)
            addTemplate(COMPOSE_COMMON_DESKTOP_PLATFORMKT)
        }


        return root
    }

    companion object {
        const val COMPOSE_SETTINGS_GRADLE = "compose-settings.gradle.kts"
        const val COMPOSE_GRADLE_PROPERTIES = "compose-gradle.properties"
        const val COMPOSE_GRADLE_WRAPPER_PROPERTIES = "compose-gradle-wrapper.properties"
        const val COMPOSE_LOCAL_PROPERTIES = "compose-local.properties"

        const val COMPOSE_DESKTOP_BUILD_GRADLE = "compose-desktop-build.gradle.kts"
        const val COMPOSE_MPP_BUILD_GRADLE = "compose-mpp-build.gradle.kts"
        const val COMPOSE_COMMON_BUILD_GRADLE = "compose-common-build.gradle.kts"
        const val COMPOSE_ANDROID_BUILD_GRADLE = "compose-android-build.gradle.kts"
        const val COMPOSE_WEB_BUILD_GRADLE = "compose-web-build.gradle.kts"

        const val COMPOSE_DESKTOP_MAINKT = "compose-desktop-main.kt"

        const val COMPOSE_ANDROID_MAINACTIVITYKT = "compose-android-MainActivity.kt"
        const val COMPOSE_ANDROID_MANIFEST = "compose-android-AndroidManifest.xml"

        const val COMPOSE_WEB_MAINKT = "compose-web-main.kt"
        const val COMPOSE_WEB_INDEX_HTML = "compose-web-index.html"

        const val COMPOSE_COMMON_ANDROID_PLATFORMKT = "compose-common-android-platform.kt"
        const val COMPOSE_COMMON_ANDROID_MANIFEST = "compose-common-AndroidManifest.xml"

        const val COMPOSE_COMMON_COMMON_APPKT = "compose-common-common-App.kt"
        const val COMPOSE_COMMON_COMMON_PLATFORMKT = "compose-common-common-platform.kt"

        const val COMPOSE_COMMON_DESKTOP_APPKT = "compose-common-desktop-App.kt"
        const val COMPOSE_COMMON_DESKTOP_PLATFORMKT = "compose-common-desktop-platform.kt"
    }
}