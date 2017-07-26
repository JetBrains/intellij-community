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
package com.jetbrains.python;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyMiscellaneousPsiOperationsTest extends PyTestCase {

  public void testQualifiedNameExtraction() {
    checkAsQualifiedNameResult("foo.bar.baz", QualifiedName.fromDottedString("foo.bar.baz"));
    checkAsQualifiedNameResult("foo().bar.baz", null);
    checkAsQualifiedNameResult("foo.bar[0]", QualifiedName.fromDottedString("foo.bar.__getitem__"));
    checkAsQualifiedNameResult("foo[0].bar", null);
    checkAsQualifiedNameResult("foo[0][0]", null);
    checkAsQualifiedNameResult("-foo.bar", QualifiedName.fromDottedString("foo.bar.__neg__"));
    checkAsQualifiedNameResult("foo + bar", QualifiedName.fromDottedString("foo.__add__"));
    checkAsQualifiedNameResult("foo + bar + baz", null);
    checkAsQualifiedNameResult("foo.bar + baz", QualifiedName.fromDottedString("foo.bar.__add__"));
    checkAsQualifiedNameResult("-foo + bar", null);
  }

  private void checkAsQualifiedNameResult(@NotNull String expression, @Nullable QualifiedName expectedQualifiedName) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(myFixture.getProject());
    final PyQualifiedExpression expr = (PyQualifiedExpression)generator.createExpressionFromText(LanguageLevel.PYTHON27, expression);
    assertEquals(expectedQualifiedName, expr.asQualifiedName());
  }

  public void testTypedVisitor() {
    class Node {
      private IElementType myType;
      private List<Node> myNodes;

      public Node(IElementType type, List<Node> nodes) {
        myType = type;
        myNodes = nodes;
      }

      @Override
      public String toString() {
        return String.format("%s(numChildren=%d)", myType, myNodes.size());
      }

      @NotNull
      public String dump() {
        final StringBuilder builder = new StringBuilder();
        dump(builder, 0);
        return builder.toString();
      }

      private void dump(StringBuilder builder, int level) {
        builder.append(StringUtil.repeat(" ", level));
        builder.append(toString());
        builder.append("\n");
        myNodes.forEach(node -> node.dump(builder, level + 1));
      }
    }

    PyTypedElementVisitor<Node> visitor = new PyTypedElementVisitor<Node>() {
      @Override
      public Node visitElement(PsiElement element) {
        final IElementType parentType = element.getNode().getElementType();
        final List<Node> childrenNodes = StreamEx.of(element.getChildren())
          .select(PyElement.class)
          .map(x -> x.acceptTyped(this))
          .toList();
        return new Node(parentType, childrenNodes);
      }
    };

    myFixture.configureByText("a.py", "class C:\n" +
                                      "    def m(self):\n" +
                                      "        if True:\n" +
                                      "            print('spam')");

    final Node root = ((PyFile)myFixture.getFile()).acceptTyped(visitor);
    assertEquals("FILE(numChildren=1)\n" +
                 " Py:CLASS_DECLARATION(numChildren=2)\n" +
                 "  Py:ARGUMENT_LIST(numChildren=0)\n" +
                 "  Py:STATEMENT_LIST(numChildren=1)\n" +
                 "   Py:FUNCTION_DECLARATION(numChildren=2)\n" +
                 "    Py:PARAMETER_LIST(numChildren=1)\n" +
                 "     Py:NAMED_PARAMETER(numChildren=0)\n" +
                 "    Py:STATEMENT_LIST(numChildren=1)\n" +
                 "     Py:IF_STATEMENT(numChildren=1)\n" +
                 "      Py:IF_IF(numChildren=2)\n" +
                 "       Py:REFERENCE_EXPRESSION(numChildren=0)\n" +
                 "       Py:STATEMENT_LIST(numChildren=1)\n" +
                 "        Py:PRINT_STATEMENT(numChildren=1)\n" +
                 "         Py:PARENTHESIZED_EXPRESSION(numChildren=1)\n" +
                 "          Py:STRING_LITERAL_EXPRESSION(numChildren=0)\n",
                 root.dump());
  }
}
