/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.remote.strprotocol.MessageHelper;
import org.testng.remote.strprotocol.TestResultMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Hani Suleiman Date: Jul 28, 2005 Time: 10:52:51 PM
 */
public class TestProxy extends AbstractTestProxy {
  @NonNls public static final Pattern COMPARISION_PATTERN =
    Pattern.compile("(.*)expected same with:\\<(.*)\\> but was:\\<(.*)\\>.*", Pattern.DOTALL);
  @NonNls public static final Pattern EXPECTED_BUT_WAS_PATTERN =
    Pattern.compile("(.*)expected:\\<(.*)\\> but was:\\<(.*)\\>.*", Pattern.DOTALL);
  @NonNls public static final Pattern EXPECTED_BUT_WAS_SET_PATTERN =
    Pattern.compile("(.*)expected \\[(.*)\\] but got \\[(.*)\\].*", Pattern.DOTALL);
  @NonNls public static final Pattern EXPECTED_NOT_SAME_BUT_WAS_PATTERN =
    Pattern.compile("(.*)expected not same with:\\<(.*)\\> but was same:\\<(.*)\\>.*", Pattern.DOTALL);
  @NonNls public static final Pattern EXPECTED_BUT_FOUND_PATTERN =
    Pattern.compile("(.*)expected \\[(.*)\\] but found \\[(.*)\\].*", Pattern.DOTALL);
  @NonNls public static final Pattern EXPECTED_BUT_WAS_HAMCREST_PATTERN =
    Pattern.compile("(.*)\nExpected: .*?\"(.*)\"\n\\s*but: .*?\"(.*)\".*", Pattern.DOTALL);
  private final List<TestProxy> results = new ArrayList<TestProxy>();
  private TestResultMessage resultMessage;
  private String name;
  private TestProxy parent;
  private SmartPsiElementPointer psiElement;
  private boolean inProgress;
  private boolean myTearDownFailure;
  private DiffHyperlink myHyperlink;

  public TestProxy() {}

  public TestProxy(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  @Override
  public boolean isConfig() {
    return false;
  }

  @Nullable
  public PsiElement getPsiElement() {
    return psiElement != null ? psiElement.getElement() : null;
  }

  public void setPsiElement(PsiElement psiElement) {
    if (psiElement != null) {
      final Project project = psiElement.getProject();
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      this.psiElement = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiElement);
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
      final PsiElement psiElement = getPsiElement();
      this.name = toDisplayText(resultMessage, psiElement != null ? psiElement.getProject() : null);
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

  public Location getLocation(@NotNull final Project project, @NotNull GlobalSearchScope searchScope) {
    if (psiElement == null) return null;
    final PsiElement element = psiElement.getElement();
    if (element == null) return null;
    return new PsiLocation<PsiElement>(project, element);
  }

  @Nullable
  public Navigatable getDescriptor(@Nullable Location location, @NotNull TestConsoleProperties properties) {
    if (location == null) return null;
    return EditSourceUtil.getDescriptor(location.getPsiElement());
  }

  @Override
  public String toString() {
    return name + ' ' + results;
  }

  public void addChild(TestProxy proxy) {
    results.add(proxy);
    proxy.setParent(this);
    proxy.setPrinter(myPrinter);
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


  public boolean isInterrupted() {
    return !isInProgress() && inProgress;
  }

  @Override
  public boolean hasPassedTests() {
    return isPassed();
  }

  @Override
  public boolean isIgnored() {
    return resultMessage != null && MessageHelper.SKIPPED_TEST == resultMessage.getResult();
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

  public void appendStacktrace(TestResultMessage result) {
    if (result.getResult() == MessageHelper.PASSED_TEST && Registry.is("testng.skip.expected.exceptions")) return;
    final String stackTrace = result.getStackTrace();
    if (stackTrace != null) {
      final List<Printable> printables = getPrintables(result);
      for (Printable printable : printables) {
        if (myHyperlink == null && printable instanceof DiffHyperlink) {
          myHyperlink = (DiffHyperlink)printable;
        }
        addLast(printable);
      }
    }
  }

  @Override
  public Long getDuration() {
    TestResultMessage message = getResultMessage();
    if (message != null) {
      return (message.getEndMillis() - message.getStartMillis());
    }
    else {
      // TODO cache?
      long duration = 0;
      for (TestProxy testProxy : getChildren()) {
        final Long d = testProxy.getDuration();
        duration += (d == null ? 0 : d.longValue());
      }
      return duration;
    }
  }

  @Override
  public boolean shouldSkipRootNodeForExport() {
    return true;
  }

  @Override
  public DiffHyperlink getDiffViewerProvider() {
    if (myHyperlink == null) {
      for (TestProxy proxy : getChildren()) {
        if (!proxy.isDefect()) continue;
        final DiffHyperlink provider = proxy.getDiffViewerProvider();
        if (provider != null) {
          return provider;
        }
      }
      return null;
    }
    return myHyperlink;
  }

  private static String trimStackTrace(String stackTrace) {
    String[] lines = stackTrace.split("\n");
    StringBuilder builder = new StringBuilder();

    if (lines.length > 0) {
      int i = lines.length - 1;
      while (i >= 0) {
        //first 4 chars are '\t at '
        int startIndex = lines[i].indexOf('a') + 3;
        if (lines[i].length() > 4 &&
            (lines[i].startsWith("org.testng.", startIndex) ||
             lines[i].startsWith("org.junit.", startIndex) ||
             lines[i].startsWith("sun.reflect.DelegatingMethodAccessorImpl", startIndex) ||
             lines[i].startsWith("sun.reflect.NativeMethodAccessorImpl", startIndex) ||
             lines[i].startsWith("java.lang.reflect.Method", startIndex) ||
             lines[i].startsWith("com.intellij.rt.execution.application.AppMain", startIndex))) {

        }
        else {
          // we're done with internals, so we know the rest are ok
          break;
        }
        i--;
      }
      for (int j = 0; j <= i; j++) {
        builder.append(lines[j]);
        builder.append('\n');
      }
    }
    return builder.toString();
  }

  private static List<Printable> getPrintables(final TestResultMessage result) {
    String s = trimStackTrace(result.getStackTrace());
    List<Printable> printables = new ArrayList<Printable>();
    //figure out if we have a diff we need to hyperlink
    if (appendDiffChuncks(result, s, printables, COMPARISION_PATTERN)) {
      return printables;
    }
    if (appendDiffChuncks(result, s, printables, EXPECTED_BUT_WAS_PATTERN)) {
      return printables;
    }
    if (appendDiffChuncks(result, s, printables, EXPECTED_BUT_WAS_SET_PATTERN)) {
      return printables;
    }
    if (appendDiffChuncks(result, s, printables, EXPECTED_NOT_SAME_BUT_WAS_PATTERN)) {
      return printables;
    }
    if (appendDiffChuncks(result, s, printables, EXPECTED_BUT_FOUND_PATTERN)) {
      return printables;
    }
    if (appendDiffChuncks(result, s, printables, EXPECTED_BUT_WAS_HAMCREST_PATTERN)) {
      return printables;
    }
    printables.add(new Chunk(s, ConsoleViewContentType.ERROR_OUTPUT));
    return printables;
  }

  private static boolean appendDiffChuncks(final TestResultMessage result, String s, List<Printable> printables, final Pattern pattern) {
    final Matcher matcher = pattern.matcher(s);
    if (matcher.matches()) {
      printables.add(new Chunk(matcher.group(1), ConsoleViewContentType.ERROR_OUTPUT));
      //we have an assert with expected/actual, so we parse it out and create a diff hyperlink
      DiffHyperlink link = new DiffHyperlink(matcher.group(2), matcher.group(3), null) {
        protected String getTitle() {
          //TODO should do some more farting about to find the equality assertion that failed and show that as title
          return result.getTestClass() + '#' + result.getMethod() + "() failed";
        }
      };
      //same as junit diff view
      printables.add(link);
      printables.add(new Chunk(trimStackTrace(s.substring(matcher.end(3) + 1)), ConsoleViewContentType.ERROR_OUTPUT));
      return true;
    }
    return false;
  }

  public static String toDisplayText(TestResultMessage message, Project project) {
    String name = message.getName();
    if (project != null && Comparing.strEqual(name, project.getName())) {
      name = message.getMethod();
    }
    final String mainNamePart = name;
    final String[] parameters = message.getParameters();
    if (parameters != null && parameters.length > 0) {
      final String[] parameterTypes = message.getParameterTypes();
      name += " (";
      for(int i= 0; i < parameters.length; i++) {
        if(i > 0) {
          name += ", ";
        }
        if(CommonClassNames.JAVA_LANG_STRING.equals(parameterTypes[i]) && !("null".equals(parameters[i]) || "\"\"".equals(parameters[i]))) {
          name += "\"" + parameters[i] + "\"";
        }
        else {
          name += parameters[i];
        }

      }
      name += ")";
    }
    final String testDescription = message.getTestDescription();
    if (testDescription != null && !Comparing.strEqual(testDescription, mainNamePart)) {
      name += " [" + testDescription + "]";
    }
    return name;
  }

  public static class Chunk implements Printable {
    public String text;
    public ConsoleViewContentType contentType;

    public void printOn(Printer printer) {
      printer.print(text, contentType);
    }

    public Chunk(String text, ConsoleViewContentType contentType) {
      this.text = text;
      this.contentType = contentType;
    }

    public String toString() {
      return text;
    }
  }
}
