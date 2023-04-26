// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.postfix;

import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateStorage;
import com.intellij.codeInsight.template.postfix.templates.LanguagePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.codeInsight.postfix.PyEditablePostfixTemplate;
import com.jetbrains.python.codeInsight.postfix.PyPostfixTemplateExpressionCondition;
import com.jetbrains.python.codeInsight.postfix.PyPostfixTemplateProvider;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;

public class PyEditablePostfixTemplatesTest extends PyPostfixTemplateTestCase {

  public void testEditableTemplateBoolean() {
    registerTemplate(createTemplate(new PyPostfixTemplateExpressionCondition.PyBooleanExpression()));
    Arrays.asList("True", "False", "True or False", "1 == 0").forEach(this::doSimpleTest);
  }

  public void testEditableTemplateNumeric() {
    registerTemplate(createTemplate(new PyPostfixTemplateExpressionCondition.PyNumberExpression()));
    Arrays.asList("1", "int(1)", "1.0", "float(1.0)", "1j", "complex(1,-1)", "1-1j").forEach(this::doSimpleTest);
  }

  public void testEditableTemplateString() {
    registerTemplate(createTemplate(new PyPostfixTemplateExpressionCondition.PyStringExpression()));
    Arrays.asList(LanguageLevel.PYTHON27, LanguageLevel.PYTHON312).forEach(level -> runWithLanguageLevel(level, () -> {
      Arrays.asList("'text'", "\"text\"", "b't'", "\"\\u0394\"").forEach(this::doSimpleTest);
    }));
  }

  public void testEditableTemplateIterable() {
    registerTemplate(createTemplate(new PyPostfixTemplateExpressionCondition.PyIterable()));
    doTest();
  }

  public void testEditableTemplateDict() {
    registerTemplate(createTemplate(new PyPostfixTemplateExpressionCondition.PyDict()));
    Arrays.asList("{}", "{'a':1}", "dict()").forEach(this::doSimpleTest);
  }

  public void testEditableTemplateList() {
    registerTemplate(createTemplate(new PyPostfixTemplateExpressionCondition.PyList()));
    Arrays.asList("[]", "[1,2,3]", "list()").forEach(this::doSimpleTest);
  }

  public void testEditableTemplateSet() {
    registerTemplate(createTemplate(new PyPostfixTemplateExpressionCondition.PySet()));
    Arrays.asList("{1,2,3}", "set()").forEach(this::doSimpleTest);
  }

  public void testEditableTemplateTuple() {
    registerTemplate(createTemplate(new PyPostfixTemplateExpressionCondition.PyTuple()));
    Arrays.asList("(1,2,3)", "tuple()").forEach(this::doSimpleTest);
  }

  public void testEditableTemplateLenCapable() {
    registerTemplate(createTemplate(new PyPostfixTemplateExpressionCondition.PyBuiltinLenApplicable()));
    Arrays.asList("{}", "[]", "set()", "tuple()", "'string'").forEach(this::doSimpleTest);
  }

  public void testEditableTemplateConcreteType() {
    // Note that we've to include file name to align with QualifiedNameFinder.getQualifiedName logic
    registerTemplate(
      createTemplate(PyPostfixTemplateExpressionCondition.PyClassCondition.Companion.create("editableTemplateConcreteType.CT")));
    doTest();
  }

  public void testEditableTemplateConcreteTypeInapplicable() {
    registerTemplate(createTemplate(PyPostfixTemplateExpressionCondition.PyClassCondition.Companion.create("list")));
    doTest();
  }

  public void testEditableTemplateExceptionCall() {
    registerTemplate(createTemplate(new PyPostfixTemplateExpressionCondition.PyExceptionExpression()));
    doTest();
  }

  public void testEditableTemplateExceptionVar() {
    registerTemplate(createTemplate(new PyPostfixTemplateExpressionCondition.PyExceptionExpression()));
    doTest();
  }

  public void testEditableTemplateExceptionExcept() {
    registerTemplate(createTemplate(new PyPostfixTemplateExpressionCondition.PyExceptionExpression()));
    doTest();
  }

  @Override
  protected String getTestDataDir() {
    return "editable/";
  }

  private static void registerTemplate(@NotNull PostfixTemplate template) {
    PostfixTemplateStorage saveStorage = new PostfixTemplateStorage();
    saveStorage.setTemplates(new PyPostfixTemplateProvider(), Collections.singletonList(template));
    PostfixTemplateStorage.getInstance().loadState(saveStorage.getState());
  }

  @NotNull
  private static PyEditablePostfixTemplate createTemplate(PyPostfixTemplateExpressionCondition condition) {
    return new PyEditablePostfixTemplate("foo", "foo", "foo($EXPR$)", "", Collections.singleton(condition), true, getProvider(), false);
  }

  private static PyPostfixTemplateProvider getProvider() {
    for (LanguageExtensionPoint point : LanguagePostfixTemplate.EP_NAME.getExtensionList()) {
      PyPostfixTemplateProvider provider = ObjectUtils.tryCast(point.getInstance(), PyPostfixTemplateProvider.class);
      if (provider != null) {
        return provider;
      }
    }
    throw new RuntimeException("PyPostfixTemplateProvider not found");
  }
}
