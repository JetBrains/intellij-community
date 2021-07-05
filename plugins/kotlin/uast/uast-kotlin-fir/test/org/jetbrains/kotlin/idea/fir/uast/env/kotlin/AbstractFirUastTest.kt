// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.uast.env.kotlin

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.ThrowableRunnable
import com.intellij.util.io.URLUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.fir.uast.invalidateAllCachesForUastTests
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.FirKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.firKotlinUastPlugin
import org.jetbrains.uast.kotlin.internal.FirCliKotlinUastResolveProviderService
import org.jetbrains.uast.test.common.kotlin.UastPluginSelection
import java.io.File
import java.nio.file.Paths

abstract class AbstractFirUastTest : KotlinLightCodeInsightFixtureTestCase(), UastPluginSelection {
    companion object {
        private const val IGNORE_FIR_DIRECTIVE = "IGNORE_FIR"

        val String.withIgnoreFirDirective: Boolean
            get() {
                return IGNORE_FIR_DIRECTIVE in KotlinTestUtils.parseDirectives(this)
            }
    }

    private fun registerExtensionPointAndServiceIfNeeded() {
        val area = Extensions.getRootArea()
        CoreApplicationEnvironment.registerExtensionPoint(
            area,
            UastLanguagePlugin.extensionPointName,
            UastLanguagePlugin::class.java
        )
        val service = FirCliKotlinUastResolveProviderService()
        project.registerServiceInstance(
            BaseKotlinUastResolveProviderService::class.java,
            service
        )
        project.registerServiceInstance(
            FirKotlinUastResolveProviderService::class.java,
            service
        )
    }

    override fun setUp() {
        super.setUp()
        registerExtensionPointAndServiceIfNeeded()
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable {
                project.invalidateAllCachesForUastTests()
            },
            ThrowableRunnable { super.tearDown() },
        )
    }

    override fun isFirPlugin(): Boolean = true

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE_FULL_JDK

    private fun getVirtualFile(filepath: String): VirtualFile {
        val vfs = VirtualFileManager.getInstance().getFileSystem(URLUtil.FILE_PROTOCOL)
        return vfs.findFileByPath(filepath)
            ?: error("Virtual file $filepath was not found in ${vfs.findFileByPath(".")?.path}")
    }

    abstract fun check(filePath: String, file: UFile)

    protected fun doCheck(filePath: String, checkCallback: (String, UFile) -> Unit = { _filePath, file -> check(_filePath, file) }) {
        check(UastLanguagePlugin.getInstances().count { it.language == KotlinLanguage.INSTANCE } == 1)
        val normalizedFile = Paths.get(filePath).normalize()
        val virtualFile = getVirtualFile(normalizedFile.toString())

        val testName = normalizedFile.fileName.toString().removeSuffix(".kt")
        val fileContent = File(virtualFile.canonicalPath!!).readText()
        if (isFirPlugin && fileContent.withIgnoreFirDirective) return

        val psiFile = myFixture.configureByText(virtualFile.name, fileContent)
        val uFile = UastFacade.convertElementWithParent(psiFile, null) ?: error("Can't get UFile for $testName")
        checkCallback(normalizedFile.toString(), uFile as UFile)
    }
}
