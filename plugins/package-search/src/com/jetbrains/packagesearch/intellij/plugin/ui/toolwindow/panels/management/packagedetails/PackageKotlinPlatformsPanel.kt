package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packagedetails

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.jetbrains.packagesearch.api.v2.ApiStandardPackage
import com.jetbrains.packagesearch.api.v2.ApiStandardPackage.ApiPlatform.PlatformTarget
import com.jetbrains.packagesearch.api.v2.ApiStandardPackage.ApiPlatform.PlatformType
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.util.HtmlEditorPane
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import javax.swing.BoxLayout

internal class PackageKotlinPlatformsPanel : HtmlEditorPane() {

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    border = emptyBorder(top = 8)
    background = PackageSearchUI.UsualBackgroundColor
  }

    fun display(platforms: List<ApiStandardPackage.ApiPlatform>) {
        clear()

        val chunks = mutableListOf<HtmlChunk>()
        chunks += HtmlChunk.p().addText(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.kotlinPlatforms"))
        chunks += HtmlChunk.ul().children(
            platforms.mapNotNull { platform ->
                val type: PlatformType = PlatformType.from(platform.type)
                @NlsSafe val displayName = type.displayName() ?: return@mapNotNull null
                HtmlChunk.li().addText(displayName).let { element ->
                    val canHaveTargets = type == PlatformType.JS || type == PlatformType.NATIVE
                    val targets = platform.targets
                    if (canHaveTargets && targets != null && targets.isNotEmpty()) {
                        element.children(
                            HtmlChunk.br(),
                            HtmlChunk.span("font-style: italic;").addText(
                                targets.mapNotNull { PlatformTarget.from(it).displayName() }.joinToString(", ")
                            )
                        )
                    } else {
                        element
                    }
                }
            }
        )
        setBody(chunks)
    }

    fun clear() {
        clearBody()
    }

    private fun PlatformType.displayName() = when (this) {
        PlatformType.JS -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.js")
        PlatformType.JVM -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.jvm")
        PlatformType.COMMON -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.common")
        PlatformType.NATIVE -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.native")
        PlatformType.ANDROID_JVM -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.android_jvm")
        PlatformType.UNSUPPORTED -> null
    }

    @Suppress("ComplexMethod") // Not really complex, just a bit ol' lookup table
    private fun PlatformTarget.displayName() = when (this) {
        PlatformTarget.NODE -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.node")
        PlatformTarget.BROWSER -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.browser")
        PlatformTarget.ANDROID_X64 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.android_x64")
        PlatformTarget.ANDROID_X86 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.android_x86")
        PlatformTarget.ANDROID_ARM32 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.android_arm32")
        PlatformTarget.ANDROID_ARM64 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.android_arm64")
        PlatformTarget.IOS_ARM32 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.ios_arm32")
        PlatformTarget.IOS_ARM64 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.ios_arm64")
        PlatformTarget.IOS_X64 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.ios_x64")
        PlatformTarget.WATCHOS_ARM32 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.watchos_arm32")
        PlatformTarget.WATCHOS_ARM64 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.watchos_arm64")
        PlatformTarget.WATCHOS_X86 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.watchos_x86")
        PlatformTarget.WATCHOS_X64 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.watchos_x64")
        PlatformTarget.TVOS_ARM64 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.tvos_arm64")
        PlatformTarget.TVOS_X64 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.tvos_x64")
        PlatformTarget.LINUX_X64 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.linux_x64")
        PlatformTarget.MINGW_X86 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.mingw_x86")
        PlatformTarget.MINGW_X64 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.mingw_x64")
        PlatformTarget.MACOS_X64 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.macos_x64")
        PlatformTarget.MACOS_ARM64 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.macos_arm64")
        PlatformTarget.LINUX_ARM64 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.linux_arm64")
        PlatformTarget.LINUX_ARM32_HFP -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.linux_arm32_hfp")
        PlatformTarget.LINUX_MIPS32 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.linux_mips32")
        PlatformTarget.LINUX_MIPSEL32 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.linux_mipsel32")
        PlatformTarget.WASM_32 -> PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform.platform.target.wasm32")
        PlatformTarget.UNSUPPORTED -> null
    }
}
