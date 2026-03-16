// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.test.sequence

import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.core.wrapper.StreamChainBuilder
import com.intellij.debugger.streams.test.StreamChainBuilderTestCase
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.IdeaTestUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.debugger.test.DEBUGGER_TESTDATA_PATH_BASE
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinPsiChainBuilderTestCase(private val relativePath: String) : StreamChainBuilderTestCase(), ExpectedPluginModeProvider {
    override val pluginMode = KotlinPluginMode.K1
    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    override fun getTestDataPath(): String = "$DEBUGGER_TESTDATA_PATH_BASE/sequence/psi/$relativeTestPath"
    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun getFileExtension(): String = ".kt"
    abstract val kotlinChainBuilder: StreamChainBuilder
    override fun getChainBuilder(): StreamChainBuilder = kotlinChainBuilder
    private val stdLibName = "kotlin-stdlib"

    protected abstract fun doTest()

    final override fun getRelativeTestPath(): String = relativePath

    override fun getProjectJDK(): Sdk {
        return IdeaTestUtil.getMockJdk9()
    }

    abstract class Positive(relativePath: String) : KotlinPsiChainBuilderTestCase(relativePath) {
        override fun doTest() {
            val chains = buildChains()
            checkChains(chains)
        }

        private fun checkChains(chains: MutableList<StreamChain>) {
            TestCase.assertFalse(chains.isEmpty())
        }
    }

    abstract class Negative(relativePath: String) : KotlinPsiChainBuilderTestCase(relativePath) {
        override fun doTest() {
            val elementAtCaret = configureAndGetElementAtCaret()
            TestCase.assertFalse(chainBuilder.isChainExists(elementAtCaret))
            TestCase.assertTrue(chainBuilder.build(elementAtCaret).isEmpty())
        }
    }
}
