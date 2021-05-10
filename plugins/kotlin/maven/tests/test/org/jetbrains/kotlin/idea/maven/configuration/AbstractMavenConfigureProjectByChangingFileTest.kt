// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven.configuration

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.kotlin.idea.configuration.AbstractConfigureProjectByChangingFileTest
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.test.AndroidStudioTestUtils
import java.io.File

abstract class AbstractMavenConfigureProjectByChangingFileTest : AbstractConfigureProjectByChangingFileTest<KotlinMavenConfigurator>() {
    override fun shouldRunTest(): Boolean {
        return super.shouldRunTest() && !AndroidStudioTestUtils.skipIncompatibleTestAgainstAndroidStudio()
    }

    fun doTestWithMaven(path: String) {
        val pathWithFile = MavenConstants.POM_XML
        doTest(pathWithFile, pathWithFile.replace("pom", "pom_after"), KotlinJavaMavenConfigurator())
    }

    fun doTestWithJSMaven(path: String) {
        val pathWithFile = MavenConstants.POM_XML
        doTest(pathWithFile, pathWithFile.replace("pom", "pom_after"), KotlinJavascriptMavenConfigurator())
    }

    override fun runConfigurator(
        module: Module,
        file: PsiFile,
        configurator: KotlinMavenConfigurator,
        version: String,
        collector: NotificationMessageCollector
    ) {
        WriteCommandAction.runWriteCommandAction(module.project) {
            configurator.configureModule(module, file, version, collector)
        }
    }

    override fun getProjectJDK(): Sdk {
        val root = KotlinTestUtils.getTestsRoot(this::class.java)
        val dir = KotlinTestUtils.getTestDataFileName(this::class.java, name)

        val pomFile = File("$root/$dir", MavenConstants.POM_XML)

        if (pomFile.readText().contains("<target>9</target>")) {
            return IdeaTestUtil.getMockJdk9()
        } else {
            return super.getProjectJDK()
        }
    }
}
