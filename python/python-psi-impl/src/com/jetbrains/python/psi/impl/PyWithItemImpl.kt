/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyWithItem
import com.jetbrains.python.psi.PyWithStatement
import com.jetbrains.python.psi.types.PyCollectionType
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.TypeEvalContext

class PyWithItemImpl(astNode: ASTNode?) : PyElementImpl(astNode), PyWithItem {
  override fun acceptPyVisitor(pyVisitor: PyElementVisitor) {
    pyVisitor.visitPyWithItem(this)
  }

  override fun isSuppressingExceptions(context: TypeEvalContext): Boolean {
    val withStmt = PsiTreeUtil.getParentOfType(this, PyWithStatement::class.java, false) ?: return false
    val abstractType = if (withStmt.isAsync) "contextlib.AbstractAsyncContextManager" else "contextlib.AbstractContextManager"
    return context.getType(expression)
      .let { PyTypeUtil.convertToType(it, abstractType, this, context) }
      .let { (it as? PyCollectionType)?.elementTypes?.getOrNull(1) }
      .let {
        it == PyBuiltinCache.getInstance(this).boolType ||
        it is PyLiteralType && PyEvaluator.getBooleanLiteralValue(it.expression) == true
      }
  }
}
