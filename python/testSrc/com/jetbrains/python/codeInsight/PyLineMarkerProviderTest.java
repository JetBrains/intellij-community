/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.lang.ASTNode;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyPossibleClassMember;
import org.hamcrest.Matchers;
import org.junit.Assert;

import javax.swing.*;
import java.awt.event.MouseEvent;


/**
 * @author Ilya.Kazakevich
 */
public final class PyLineMarkerProviderTest extends PyTestCase {

  /**
   * Checks method has "up" arrow when overrides, and this arrow works
   */
  public void testOverriding() {
    myFixture.copyDirectoryToProject("lineMarkerTest", "");
    myFixture.configureByFile("spam.py");

    final ASTNode functionNode = myFixture.getElementAtCaret().getNode();
    // We need IDENTIFIER node
    final ASTNode[] functionChildren = functionNode.getChildren(TokenSet.create(PyTokenTypes.IDENTIFIER));
    assert functionChildren.length == 1 : "Wrong number of identifiers: " + functionChildren.length;
    final PsiElement element = functionChildren[0].getPsi();
    @SuppressWarnings("unchecked")
    final LineMarkerInfo<PsiElement> lineMarkerInfo = new PyLineMarkerProvider().getLineMarkerInfo(element);
    Assert.assertNotNull("No gutter displayed", lineMarkerInfo);
    final GutterIconNavigationHandler<PsiElement> handler = lineMarkerInfo.getNavigationHandler();
    Assert.assertNotNull("Gutter has no navigation handle", handler);
    handler.navigate(new MouseEvent(new JLabel(), 0, 0, 0, 0, 0, 0, false), element);
    final NavigatablePsiElement[] targets = PyLineMarkerNavigator.getNavigationTargets(element);
    Assert.assertNotNull("No navigation targets found", targets);

    Assert.assertThat("Wrong number of targets found", targets, Matchers.arrayWithSize(1));
    final NavigatablePsiElement parentMethod = targets[0];
    Assert.assertThat("Navigation target has wrong type", parentMethod, Matchers.instanceOf(PyPossibleClassMember.class));
    final PyClass parentClass = ((PyPossibleClassMember)parentMethod).getContainingClass();
    Assert.assertNotNull("Function overrides other function, but no parent displayed", parentClass);
    Assert.assertEquals("Wrong parent class name", "Eggs", parentClass.getName());
  }
}