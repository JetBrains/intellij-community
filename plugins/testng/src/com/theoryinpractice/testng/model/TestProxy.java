/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.theoryinpractice.testng.model;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.stacktrace.StackTraceLine;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.Printable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.LineTokenizer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.theoryinpractice.testng.ui.TestNGConsoleView;
import org.jetbrains.annotations.Nullable;
import org.testng.remote.strprotocol.MessageHelper;
import org.testng.remote.strprotocol.TestResultMessage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Hani Suleiman Date: Jul 28, 2005 Time: 10:52:51 PM
 */
public class TestProxy extends AbstractTestProxy {
  private final List<TestProxy> results = new ArrayList<TestProxy>();
  private TestResultMessage resultMessage;
  private String name;
  private TestProxy parent;
  private SmartPsiElementPointer psiElement;
  private boolean inProgress;
  private int myExceptionMark;
  private boolean myTearDownFailure;

  public TestProxy() {}

  public TestProxy(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  @Nullable
  public PsiElement getPsiElement() {
    return psiElement != null ? psiElement.getElement() : null;
  }

  public void setPsiElement(PsiElement psiElement) {
    if (psiElement != null) {
      final Project project = psiElement.getProject();
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      this.psiElement = SmartPointerManager.getInstance(project).createLazyPointer(psiElement);
    } else {
      this.psiElement = null;
    }
  }

  public boolean isResult() {
    return resultMessage != null;
  }

  public List<AbstractTestProxy> getResults(Filter filter) {
    return filter.select(results);
  }

  public List<TestProxy> getChildren() {
    return results;
  }

  public TestResultMessage getResultMessage() {
    return resultMessage;
  }

  public void setResultMessage(final TestResultMessage resultMessage) {
    //if we have a result, then our parent is a class, so we can look up our method
    //this is a bit fragile as it assumes parent is set first and correctly
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        PsiClass psiClass = (PsiClass)getParent().getPsiElement();
        if (psiClass != null) {
          PsiMethod[] methods = psiClass.getAllMethods();
          for (PsiMethod method : methods) {
            if (method.getName().equals(resultMessage.getMethod())) {
              setPsiElement(method);
              break;
            }
          }
        }
      }
    });

    TestProxy current = this;
    while (current != null) {
      current.inProgress = resultMessage.getResult() == MessageHelper.TEST_STARTED;
      current = current.getParent();
    }
    if (this.resultMessage == null || this.resultMessage.getResult() == MessageHelper.TEST_STARTED) {
      this.resultMessage = resultMessage;
      this.name = resultMessage.toDisplayString();
    }
  }

  public boolean isInProgress() {
    final TestProxy parentProxy = getParent();
    return (parentProxy == null || parentProxy.isInProgress()) && inProgress;
  }

  public boolean isDefect() {
    return isNotPassed();
  }

  public boolean shouldRun() {
    return true;
  }

  public int getMagnitude() {
    return -1;
  }

  public boolean isLeaf() {
    return isResult();
  }

  public boolean isPassed() {
    return !isNotPassed();
  }

  public Location getLocation(final Project project) {
    if (psiElement == null) return null;
    final PsiElement element = psiElement.getElement();
    if (element == null) return null;
    return new PsiLocation<PsiElement>(project, element);
  }

  public Navigatable getDescriptor(final Location location) {
    if (location == null) return null;
    if (isNotPassed()) {
      final PsiLocation<?> psiLocation = location.toPsiLocation();
      final PsiClass containingClass = psiLocation.getParentElement(PsiClass.class);
      if (containingClass != null) {
        String containingMethod = null;
        for (Iterator<Location<PsiMethod>> iterator = psiLocation.getAncestors(PsiMethod.class, false); iterator.hasNext();) {
          final PsiMethod psiMethod = iterator.next().getPsiElement();
          if (containingClass.equals(psiMethod.getContainingClass())) containingMethod = psiMethod.getName();
        }
        if (containingMethod != null) {
          final String qualifiedName = containingClass.getQualifiedName();
          for (Printable aStackTrace : myNestedPrintables) {
            if (aStackTrace instanceof TestNGConsoleView.Chunk) {
              final String[] stackTrace = new LineTokenizer(((TestNGConsoleView.Chunk)aStackTrace).text).execute();
              for (String line : stackTrace) {
                final StackTraceLine stackLine = new StackTraceLine(containingClass.getProject(), line);
                if (containingMethod.equals(stackLine.getMethodName()) && Comparing.strEqual(qualifiedName, stackLine.getClassName())) {
                  return stackLine.getOpenFileDescriptor(containingClass.getContainingFile().getVirtualFile());
                }
              }
            }
          }
        }
      }
    }
    return EditSourceUtil.getDescriptor(location.getPsiElement());
  }

  @Override
  public String toString() {
    return name + ' ' + results;
  }

  public void addChild(TestProxy proxy) {
    results.add(proxy);
    proxy.setParent(this);
    addLast(proxy);
  }

  public void setParent(TestProxy parent) {
    this.parent = parent;
  }

  public TestProxy getParent() {
    return parent;
  }

  public boolean isNotPassed() {
    if (resultNotPassed()) return true;
    //we just added the node, so we don't know if it has passes or fails
    if (resultMessage == null && results.size() == 0) return true;
    for (TestProxy child : results) {
      if (child.isNotPassed()) return true;
    }
    return false;
  }

  private boolean resultNotPassed() {
    return resultMessage != null && resultMessage.getResult() != MessageHelper.PASSED_TEST;
  }

  public List<TestProxy> getAllTests() {
    List<TestProxy> total = new ArrayList<TestProxy>();
    total.add(this);
    for (TestProxy child : results) {
      total.addAll(child.getAllTests());
    }
    return total;
  }

  public int getChildCount() {
    return results.size();
  }

  public TestProxy getChildAt(int i) {
    return results.get(i);
  }

  public TestProxy getFirstDefect() {
    for (TestProxy child : results) {
      if (child.isNotPassed() && child.isResult()) return child;
      TestProxy firstDefect = child.getFirstDefect();
      if (firstDefect != null) return firstDefect;
    }
    return null;
  }

  public int getExceptionMark() {//todo
    if (myExceptionMark == 0 && getChildCount() > 0) {
      return getChildAt(0).getExceptionMark();
    }
    return myExceptionMark;
  }

  public void setExceptionMark(int exceptionMark) {
    myExceptionMark = exceptionMark;
  }

  public boolean isInterrupted() {
    return !isInProgress() && inProgress;
  }

  public boolean isTearDownFailure() {
    for (TestProxy result : results) {
      if (result.isTearDownFailure()) return true;
    }
    return myTearDownFailure;
  }

  public void setTearDownFailure(boolean tearDownFailure) {
    myTearDownFailure = tearDownFailure;
  }
}
