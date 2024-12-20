// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.jetbrains.idea.maven.project.MavenDirectoryCompletionContributor
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.junit.Test

class MavenDirectoryCompletionContributorTest : MavenMultiVersionImportingTestCase() {
  @Test
  fun testVariants() = runBlocking {
    createProjectPom("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <packaging>pom</packaging>
                      <version>1</version>
                      <modules>
                        <module>module</module>
                      </modules>
                      <build>
                        <sourceDirectory>customSrc</sourceDirectory>
                      </build>""")

    val module = createModulePom("module",
                                 """
                                  <groupId>test</groupId>
                                  <artifactId>module</artifactId>
                                  <version>1</version>""")

    importProjectAsync()

    suspend fun check(dir: VirtualFile, vararg expected: Pair<String, JpsModuleSourceRootType<*>>) {
      readAction {
        val psiDir = PsiManager.getInstance(project).findDirectory(dir)!!
        Assertions.assertThat(MavenDirectoryCompletionContributor().getVariants(psiDir).map {
          FileUtil.getRelativePath(dir.path, FileUtil.toSystemIndependentName(it.path), '/') to it.rootType
        }).containsExactlyInAnyOrder(*expected)
      }
    }

    val resources = defaultResources().map { it to JavaResourceRootType.RESOURCE } + defaultTestResources().map { it to JavaResourceRootType.TEST_RESOURCE }

    check(projectRoot,
          "customSrc" to JavaSourceRootType.SOURCE,
          "src/test/java" to JavaSourceRootType.TEST_SOURCE,
          *resources.toTypedArray())

    check(module.parent,
          "src/main/java" to JavaSourceRootType.SOURCE,
          "src/test/java" to JavaSourceRootType.TEST_SOURCE,
          *resources.toTypedArray())

  }
}