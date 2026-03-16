// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.propertyBased

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.propertyBased.DeleteRange
import com.intellij.testFramework.propertyBased.InvokeIntention
import com.intellij.testFramework.propertyBased.MadTestingAction
import com.intellij.testFramework.propertyBased.MadTestingUtil
import com.intellij.testFramework.propertyBased.StripTestDataMarkup
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.PropertyChecker
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.quickfix.AbstractImportFixInfo
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import java.io.File
import java.util.function.Function
import java.util.function.Supplier

@SkipSlowTestLocally
abstract class KotlinCodeInsightSanityTest : KotlinLightCodeInsightFixtureTestCase() {
    private val seed: String? = System.getProperty("seed")

    override fun setUp() {
        super.setUp()
        // to avoid failures from some plugins
        assertEquals(
            "Set system property -Djavax.xml.parsers.SAXParserFactory=com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl",
            "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl",
            System.getProperty("javax.xml.parsers.SAXParserFactory")
        )

        RecursionManager.disableMissedCacheAssertions(testRootDisposable)
    }

    override fun tearDown() {
        // remove jdk if it was created during highlighting to avoid leaks
        try {
            JavaAwareProjectJdkTableImpl.removeInternalJdkInTests()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()

    fun testRandomActivity() {
        enableInspections()
        AbstractImportFixInfo.ignoreModuleError(testRootDisposable)
        val actionSupplier = actionOnKotlinFiles { file: PsiFile ->
            Generator.sampledFrom(
                InvokeIntention(file, createIntentionPolicy()),
                //TODO: support completion mutators
                //InvokeCompletion(file, KotlinCompletionPolicy()),
                StripTestDataMarkup(file),
                DeleteRange(file)
            )
        }
        PropertyChecker
            .customized()
            .run {
                seed?.let { this.rechecking(it) } ?: this
            }
            .checkScenarios(actionSupplier)
    }

    protected abstract fun createIntentionPolicy(): KotlinIntentionPolicy

    private fun enableInspections() {
        MadTestingUtil.enableAllInspections(project, KotlinLanguage.INSTANCE)
    }

    private fun actionOnKotlinFiles(fileActions: Function<PsiFile, Generator<out MadTestingAction>>): Supplier<MadTestingAction?> {
        return MadTestingUtil.actionsOnFileContents(myFixture, PathManager.getHomePath(),
                                                    { f: File -> f.name.endsWith(".kt") }, fileActions)
    }
}
