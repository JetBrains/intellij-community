// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.filters;

import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.template.emmet.EmmetAbbreviationTestSuite;
import com.intellij.openapi.util.Disposer;
import junit.framework.Test;
import org.jetbrains.annotations.NotNull;

public class BemEmmetFilterTest extends EmmetAbbreviationTestSuite {
  public BemEmmetFilterTest() {
    addBemTests();
    addBem2Tests();
    addRegressionTests();
    addConfigurableTests();
  }

  public static Test suite() {
    return new BemEmmetFilterTest();
  }

  private void addBemTests() {
    addTest(".b_m|bem", "<div class=\"b b_m\"></div>");
    addTest(".b_m1._m2|bem", "<div class=\"b b_m1 b_m2\"></div>");
    addTest(".b>._m|bem", "<div class=\"b\">\n\t<div class=\"b b_m\"></div>\n</div>");
    addTest(".b>._m1>._m2|bem", "<div class=\"b\">\n\t<div class=\"b b_m1\">\n\t\t<div class=\"b b_m2\"></div>\n\t</div>\n</div>");
    addTest(".b>.__e|bem", "<div class=\"b\">\n\t<div class=\"b__e\"></div>\n</div>");
    addTest(".b>.-e|bem", "<div class=\"b\">\n\t<div class=\"b__e\"></div>\n</div>");
    addTest(".b>.__e>.__e|bem", "<div class=\"b\">\n\t<div class=\"b__e\">\n\t\t<div class=\"b__e\"></div>\n\t</div>\n</div>");
    addTest(".b>.__e1>.____e2|bem", "<div class=\"b\">\n\t<div class=\"b__e1\">\n\t\t<div class=\"b__e2\"></div>\n\t</div>\n</div>");
    addTest(".b>.-e1>.-e2|bem", "<div class=\"b\">\n\t<div class=\"b__e1\">\n\t\t<div class=\"b__e2\"></div>\n\t</div>\n</div>");
    addTest(".b1>.b2_m1>.__e1+.____e2_m2|bem",
            "<div class=\"b1\">\n\t<div class=\"b2 b2_m1\">\n\t\t<div class=\"b2__e1\"></div>\n\t\t<div class=\"b1__e2 b1__e2_m2\"></div>\n\t</div>\n</div>");
    addTest(".b>.__e1>.__e2|bem", "<div class=\"b\">\n\t<div class=\"b__e1\">\n\t\t<div class=\"b__e2\"></div>\n\t</div>\n</div>");
    addTest(".b>.__e1>.____e2|bem", "<div class=\"b\">\n\t<div class=\"b__e1\">\n\t\t<div class=\"b__e2\"></div>\n\t</div>\n</div>");
    addTest(".b._mod|bem", "<div class=\"b b_mod\"></div>");
    addTest("form.search-form._wide>input.-query-string+input:s.-btn_large|bem",
            "<form action=\"\" class=\"search-form search-form_wide\"><input type=\"text\" class=\"search-form__query-string\"><input\n" +
            "        type=\"submit\" value=\"\" class=\"search-form__btn search-form__btn_large\"></form>");
    addTest(".b1-div>.b2_m1>.-e1+.--e2_m2|bem", """
      <div class="b1-div">
          <div class="b2 b2_m1">
              <div class="b2__e1"></div>
              <div class="b1-div__e2 b1-div__e2_m2"></div>
          </div>
      </div>""");
  }

  private void addBem2Tests() {
    addTest(".b_m1._m2|bem", "<div class=\"b b_m1 b_m2\"></div>");
    addTest(".b._mod|bem", "<div class=\"b b_mod\"></div>");
  }

  private void addRegressionTests() {
    addTest(".name__name2__name3__name4|bem", "<div class=\"name__name2__name3__name4\"></div>");
    addTest(".name__name2__name3|bem", "<div class=\"name__name2__name3\"></div>");
    addTest(".news(.title.-title(.-text))|bem", """
      <div class="news">
          <div class="title news__title">
              <div class="title__text"></div>
          </div>
      </div>""");
  }

  private void addConfigurableTests() {
    addTest(".b9m|bem", "<div class=\"b b9m\"></div>", createBemTestInitializer("__", "9", "-"), "html");
    addTest(".b9m|bem", "<div class=\"b9mb9m\"></div>", createBemTestInitializer("", "", ""), "html");
    addTest(".b>.Ыe|bem", """
      <div class="b">
          <div class="bЫe"></div>
      </div>""", createBemTestInitializer("Ы", "_", "-"), "html");
  }

  @NotNull
  private static TestInitializer createBemTestInitializer(String elementSeparator, String modifierSeparator, String shortPrefix) {
    return (fixture, testRootDisposable) -> {
      EmmetOptions options = EmmetOptions.getInstance();
      String oldElementSeparator = options.getBemElementSeparator();
      String oldModifierSeparator = options.getBemModifierSeparator();
      String oldShortPrefix = options.getBemShortElementPrefix();

      options.setBemElementSeparator(elementSeparator);
      options.setBemModifierSeparator(modifierSeparator);
      options.setBemShortElementPrefix(shortPrefix);

      Disposer.register(testRootDisposable, () -> {
        options.setBemElementSeparator(oldElementSeparator);
        options.setBemModifierSeparator(oldModifierSeparator);
        options.setBemShortElementPrefix(oldShortPrefix);
      });
    };
  }

  private void addTest(String sourceData, String expectedData) {
    addTest(sourceData, expectedData, "html");
  }
}
