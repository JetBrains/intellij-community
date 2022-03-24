// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import org.assertj.core.api.Assertions
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import org.jetbrains.idea.maven.project.MavenDirectoryCompletionContributor
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.junit.Test

class MavenDirectoryCompletionContributorTest : MavenMultiVersionImportingTestCase() {

  @Test
  fun testVariants() {
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

    importProject()

    fun check(dir: VirtualFile, vararg expected: Pair<String, JpsModuleSourceRootType<*>>) {
      val psiDir = ApplicationManager.getApplication().runReadAction<PsiDirectory> {
        PsiManager.getInstance(myProject).findDirectory(dir)!!
      }

      Assertions.assertThat(MavenDirectoryCompletionContributor().getVariants(psiDir).map {
        FileUtil.getRelativePath(dir.path, FileUtil.toSystemIndependentName(it.path), '/') to it.rootType
      }).containsExactlyInAnyOrder(*expected)
    }

    check(myProjectRoot,
          "customSrc" to JavaSourceRootType.SOURCE,
          "src/main/resources" to JavaResourceRootType.RESOURCE,
          "src/test/java" to JavaSourceRootType.TEST_SOURCE,
          "src/test/resources" to JavaResourceRootType.TEST_RESOURCE)

    check(module.parent,
          "src/main/java" to JavaSourceRootType.SOURCE,
          "src/main/resources" to JavaResourceRootType.RESOURCE,
          "src/test/java" to JavaSourceRootType.TEST_SOURCE,
          "src/test/resources" to JavaResourceRootType.TEST_RESOURCE)

  }
}