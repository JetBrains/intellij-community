// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.core.script.v1.ScratchFileOptionsByFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.defaultScratchJavaHome
import org.jetbrains.kotlin.idea.jvm.shared.scratch.scratchModuleSdkHome
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

class K2ScratchJdkSelectTest : LightJavaCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

    

    fun testModuleJdkOverridesSelectedJdkHome() {
        val scratchFile = createScratchFile()
        val moduleSdk = checkNotNull(ModuleRootManager.getInstance(myFixture.module).sdk) {
            "Light test fixture must expose a module SDK"
        }

        scratchFile.setModule(myFixture.module)
        scratchFile.saveOptions { copy(selectedJdkHome = "/definitely/not/a/real/jdk/home") }

        assertEquals("moduleJdk should resolve to the module SDK", moduleSdk, scratchFile.jdk)
        assertEquals("selectedJdk must prefer moduleJdk over the persisted home", moduleSdk, scratchFile.jdk)
        assertEquals(
            "editor jdkSupplier path must mirror moduleJdk",
            moduleSdk.homePath,
            scratchModuleSdkHome(project, scratchFile.virtualFile),
        )
    }

    fun testSelectedJdkHomeUsedWhenNoModule() {
        val scratchFile = createScratchFile()
        scratchFile.setModule(null)
        scratchFile.saveOptions { copy(selectedJdkHome = null) }

        assertNotNull(
            "defaultScratchJavaHome must resolve from JAVA_HOME / java.home in the test JVM",
            defaultScratchJavaHome,
        )
        assertEquals(
            "selectedJdk must fall back to defaultJdk (i.e., defaultScratchJavaHome)",
            defaultScratchJavaHome,
            scratchFile.jdk?.homePath,
        )
    }

    fun testModuleJdkIsNullWhenBoundModuleHasNoJavaSdk() {
        val scratchFile = createScratchFile()
        scratchFile.setModule(myFixture.module)
        val originalSdk = ModuleRootManager.getInstance(myFixture.module).sdk
        WriteAction.runAndWait<RuntimeException> {
            ModuleRootManager.getInstance(myFixture.module).modifiableModel.apply {
                sdk = null
                commit()
            }
        }
        try {
            assertNull(
                "moduleJdk must be null while the bound module has no SDK (combobox would stay enabled)",
                scratchFile.jdk,
            )
        } finally {
            val restore = originalSdk ?: ProjectJdkTable.getInstance().allJdks.firstOrNull()
            if (restore != null) {
                WriteAction.runAndWait<RuntimeException> {
                    ModuleRootManager.getInstance(myFixture.module).modifiableModel.apply {
                        sdk = restore
                        commit()
                    }
                }
            }
        }
    }

    private fun createScratchFile(): ScratchFile {
        // The file extension is intentionally non-`.kts` so opening it in the editor doesn't trip
        // `KotlinScriptEditorListener` → `KotlinScriptService.scheduleLoading`, which races test
        // teardown and produces `InvalidVirtualFileAccessException` noise. ScratchFile's options
        // and module name are file-gist / file-attribute backed and don't care about the extension.
        val name = "K2ScratchJdkSelectTest_${getTestName(false)}.txt"
        val vFile = myFixture.configureByText(name, "").virtualFile
        return TestScratchFile(project, vFile)
    }

    /** Minimal in-test scratch file: persists module name without triggering K2's editor reload. */
    private class TestScratchFile(project: Project, virtualFile: VirtualFile) : ScratchFile(project, virtualFile) {
        override fun setModule(module: Module?) {
            ScratchFileOptionsByFile.update(project, virtualFile) {
                copy(selectedModule = module?.name)
            }
        }
    }
}
