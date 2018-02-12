/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.uast.test.java

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.RenderLogTestBase
import org.jetbrains.uast.visitor.UastVisitor
import java.io.File

abstract class AbstractJavaRenderLogTest : AbstractJavaUastTest(), RenderLogTestBase {
  override fun getTestFile(testName: String, ext: String) =
    File(File(TEST_JAVA_MODEL_DIR, testName).canonicalPath.substringBeforeLast('.') + '.' + ext)


  override fun check(testName: String, file: UFile, checkParentConsistency: Boolean) {
    super.check(testName, file, checkParentConsistency)

    file.accept(object : UastVisitor {
      override fun visitElement(node: UElement): Boolean {
        if (node is PsiElement) {
          UsefulTestCase.assertInstanceOf(node.containingFile, PsiJavaFile::class.java)
        }
        return false
      }
    })
  }

}