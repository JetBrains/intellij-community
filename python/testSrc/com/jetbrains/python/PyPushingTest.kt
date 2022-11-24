// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.impl.FilePropertyPusher
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.rules.TempDirectory
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher
import com.jetbrains.python.sdk.pythonSdk
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

@RunsInEdt
class PyPushingTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val edtRule = EdtRule()

  @Rule
  @JvmField
  val projectModelRule = ProjectModelRule()

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val tempDirectory = TempDirectory()

  @Rule
  @JvmField
  val testName = TestName()

  val project: Project get() = projectModelRule.project

  @Test
  fun `language level shouldn't be pushed into excluded directories`() {
    val module = projectModelRule.createPythonModule("moduleName")
    val contentRootDir = projectModelRule.baseProjectDir.newVirtualDirectory("contentRoot")
    PsiTestUtil.addContentRoot(module, contentRootDir)

    ModuleRootModificationUtil.updateExcludedFolders(module, contentRootDir, emptyList(), listOf(contentRootDir.url + "/excluded"))
    val excludedDir = projectModelRule.baseProjectDir.newVirtualDirectory("contentRoot/excluded")
    excludedDir.writeChild("someDir/ExcludedFile.py", "")
    val languageLevel = LanguageLevel.PYTHON312
    val sdk = PythonMockSdk.create(languageLevel)
    module.pythonSdk = sdk

    val pusher = FilePropertyPusher.EP_NAME.findExtension(PythonLanguageLevelPusher::class.java)
    Assertions.assertThat(pusher).withFailMessage("Failed to find pusher").isNotNull
    val key = pusher!!.fileDataKey
    Assertions.assertThat(key.getPersistentValue(contentRootDir)).isEqualTo(languageLevel)
    Assertions.assertThat(key.getPersistentValue(excludedDir)).withFailMessage {
      "Directory $excludedDir is excluded before creation, " +
      "and should have no language version pushed"
    }.isNull()
    val child = excludedDir.findChild("someDir")
    Assertions.assertThat(key.getPersistentValue(child)).withFailMessage {
      "Directory $child is under excluded directory, " +
      "and should have no language version pushed"
    }.isNull()
  }

  private fun ProjectModelRule.createPythonModule(moduleName: String): Module {
    val moduleRoot = baseProjectDir.newVirtualDirectory(moduleName)
    return WriteCommandAction.writeCommandAction(project).compute(
      ThrowableComputable<Module, RuntimeException> {
        val moduleModel = ModuleManager.getInstance(project).getModifiableModel()
        val module = moduleModel.newModule(moduleRoot.toNioPath().resolve("$moduleName.iml"), PythonModuleTypeBase.getInstance().id)
        moduleModel.commit()
        module
      }
    )
  }
}