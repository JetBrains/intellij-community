// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test.env.kotlin

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.io.URLUtil
import org.jetbrains.fir.uast.test.invalidateAllCachesForUastTests
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.KtAssert
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.evaluation.UEvaluatorExtension
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.FirKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.evaluation.KotlinEvaluatorExtension
import org.jetbrains.uast.kotlin.internal.FirCliKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.FirKotlinUastLibraryPsiProviderService
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

abstract class AbstractFirUastTest : KotlinLightCodeInsightFixtureTestCase() {
    companion object {

        val String.withIgnoreFirDirective: Boolean
            get() {
                return IgnoreTests.DIRECTIVES.IGNORE_K2.replace("// ", "") in KotlinTestUtils.parseDirectives(this)
            }
    }

    protected open val testBasePath: Path? = null

    private fun registerExtensionPointAndServiceIfNeeded() {
        val area = Extensions.getRootArea()
        CoreApplicationEnvironment.registerExtensionPoint(
          area,
          UastLanguagePlugin.EP,
          UastLanguagePlugin::class.java
        )
        area.getExtensionPoint(UEvaluatorExtension.EXTENSION_POINT_NAME).registerExtension(KotlinEvaluatorExtension(), project)
        val service = FirCliKotlinUastResolveProviderService()
        val application = ApplicationManager.getApplication()
        application.registerServiceInstance(
            BaseKotlinUastResolveProviderService::class.java,
            service
        )

        application.registerServiceInstance(
            FirKotlinUastResolveProviderService::class.java,
            service
        )

        application.registerServiceInstance(
            FirKotlinUastLibraryPsiProviderService::class.java,
            FirKotlinUastLibraryPsiProviderService.Default(),
        )
    }

    override fun setUp() {
        super.setUp()
        registerExtensionPointAndServiceIfNeeded()
    }

    override fun tearDown() {
        runAll(
            { project.invalidateAllCachesForUastTests() },
            { super.tearDown() },
        )
    }

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

    private fun getVirtualFile(filepath: String): VirtualFile {
        val vfs = VirtualFileManager.getInstance().getFileSystem(URLUtil.FILE_PROTOCOL)
        return vfs.findFileByPath(filepath)
            ?: error("Virtual file $filepath was not found in ${vfs.findFileByPath(".")?.path}")
    }

    abstract fun check(filePath: String, file: UFile)

    open fun isExpectedToFail(filePath: String, fileContent: String): Boolean {
        return fileContent.withIgnoreFirDirective
    }

    protected fun doCheck(filePath: String, checkCallback: (String, UFile) -> Unit = { _filePath, file -> check(_filePath, file) }) {
        check(UastLanguagePlugin.getInstances().count { it.language == KotlinLanguage.INSTANCE } == 1)
        val normalizedFile = Paths.get(filePath).let { testBasePath?.resolve(it) ?: it }.normalize()
        val virtualFile = getVirtualFile(normalizedFile.toString())

        val testName = normalizedFile.fileName.toString().removeSuffix(".kt")
        val fileContent = File(virtualFile.canonicalPath!!).readText()

        val psiFile = myFixture.configureByText(virtualFile.name, fileContent)
        val uFile = UastFacade.convertElementWithParent(psiFile, null) ?: error("Can't get UFile for $testName")
        try {
            checkCallback(normalizedFile.toString(), uFile as UFile)
        } catch (e: Throwable) {
            if (isExpectedToFail(filePath, fileContent))
                return
            else
                throw e
        }
        if (isExpectedToFail(filePath, fileContent)) {
            KtAssert.fail("This test seems not fail anymore. Drop this from the white-list and re-run the test.")
        }
    }
}
