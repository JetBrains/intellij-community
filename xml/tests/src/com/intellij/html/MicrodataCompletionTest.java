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
package com.intellij.html;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;

import java.util.Arrays;
import java.util.List;

/**
 * @author: Fedor.Korotkov
 */
public class MicrodataCompletionTest extends CodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return "/xml/tests/testData/microdata/";
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }

  private void doTestInHtml(String text, String... items) {
    configureAndComplete(text);
    assertContainsElements(myFixture.getLookupElementStrings(), Arrays.asList(items));
  }

  private void doFailTestInHtml(String text, String... items) {
    configureAndComplete(text);
    final List<String> lookups = myFixture.getLookupElementStrings();
    assertNotNull(lookups);
    for (String item : items) {
      assertFalse("Should not contain: " + item, lookups.contains(item));
    }
  }

  private void configureAndComplete(String text) {
    myFixture.configureByText(StdFileTypes.HTML, text);
    myFixture.complete(CompletionType.BASIC);
  }

  public void testScopeType() {
    doTestInHtml("<section <caret>></section>", "itemscope");
  }

  public void testScopeInDivTag() {
    doTestInHtml("<div <caret>></div>", "itemscope");
  }

  public void testScopeInSpanTag() {
    doTestInHtml("<span <caret>></span>", "itemscope");
  }

  public void testScopeInATag() {
    doTestInHtml("<a <caret>></a>", "itemscope");
  }

  public void _testTypeInATag() {
    doFailTestInHtml("<a <caret>></a>", "itemtype", "itemid");
  }

  public void testTypeInScope() {
    doTestInHtml("<section itemscope <caret>><div></div></section>", "itemtype", "itemid");
  }

  public void testPropInScope() {
    doTestInHtml("<section itemscope><div <caret>></div></section>", "itemprop");
  }

  public void _testTypeWithinScope() {
    doFailTestInHtml("<section <caret>><div></div></section>", "itemtype", "itemid");
  }

  public void _testPropWithinScope() {
    doFailTestInHtml("<section><div <caret>></div></section>", "itemprop");
  }

  public void testPropWithinScopeWithRef() {
    doTestInHtml("<body>" +
                 "<section itemscope itemref=\"bar foo\"></section>" +
                 "<section id=\"foo\"><div <caret>></div></section>" +
                 "</body>", "itemprop");
  }

  public void testRefInScope() {
    doTestInHtml("<section itemscope itemref=\"b<caret>\"></section><p id=\"bar\"></p><p id=\"baz\"></p>", "bar", "baz");
  }

  public void testPropValue() {
    final VirtualFile virtualFile = myFixture.copyFileToProject("Person.html");
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().addResource("http://data-vocabulary.org/Person", virtualFile.getPath()));
    doTestInHtml("<section itemscope itemtype=\"http://data-vocabulary.org/Person\"><div itemprop=\"<caret>\"></div></section>",
                 "name", "nickname", "photo", "title", "role", "url", "affiliation", "friend", "acquaintance", "address"
    );
  }

  public void testPropValueSchemaOrgFormat() {
    final VirtualFile virtualFile = myFixture.copyFileToProject("Product.html");
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().addResource("http://schema.org/Product", virtualFile.getPath()));
    doTestInHtml("<section itemscope itemtype=\"http://schema.org/Product\"><div itemprop=\"<caret>\"></div></section>",
                 "additionalType",
                 "aggregateRating",
                 "brand",
                 "color",
                 "depth",
                 "description",
                 "gtin13",
                 "gtin14",
                 "gtin8",
                 "height",
                 "image",
                 "isAccessoryOrSparePartFor",
                 "isConsumableFor",
                 "isRelatedTo",
                 "isSimilarTo",
                 "itemCondition",
                 "logo",
                 "manufacturer",
                 "model",
                 "mpn",
                 "name",
                 "offers",
                 "productID",
                 "releaseDate",
                 "review",
                 "reviews",
                 "sku",
                 "url",
                 "weight",
                 "width"
    );
  }

  public void testPropValueSchemaOrgFormatWithLinks() {
    final VirtualFile virtualFile = myFixture.copyFileToProject("Rating.html");
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().addResource("http://schema.org/Rating", virtualFile.getPath()));
    doTestInHtml("<section itemscope itemtype=\"http://schema.org/Rating\"><div itemprop=\"<caret>\"></div></section>",
                 "alternateName",
                 "bestRating",
                 "description",
                 "image",
                 "name",
                 "potentialAction",
                 "ratingValue",
                 "reviewRating",
                 "sameAs",
                 "url",
                 "worstRating"
    );
  }

  public void testPropValueFromTwoTypes() {
    final VirtualFile personFile = myFixture.copyFileToProject("Person.html");
    final VirtualFile addressFile = myFixture.copyFileToProject("Address.html");
    ApplicationManager.getApplication().runWriteAction(() -> {
      ExternalResourceManager.getInstance().addResource("http://data-vocabulary.org/Person", personFile.getPath());
      ExternalResourceManager.getInstance().addResource("http://data-vocabulary.org/Address", addressFile.getPath());
    });
    doTestInHtml(
      "<section itemscope itemtype=\"http://data-vocabulary.org/Person http://data-vocabulary.org/Address\"><div itemprop=\"<caret>\"></div></section>",
      "name", "nickname", "photo", "title", "role", "url", "affiliation", "friend", "acquaintance", "address",
      "street-address", "locality", "region", "postal-code", "country-name"
    );
  }

  public void testPropValueFromRef() {
    final VirtualFile virtualFile = myFixture.copyFileToProject("Person.html");
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().addResource("http://data-vocabulary.org/Person", virtualFile.getPath()));
    doTestInHtml("<body>" +
                 "<section itemscope itemtype=\"http://data-vocabulary.org/Person\" itemref=\"foo\"></section>" +
                 "<section id=\"foo\"><div itemprop=\"<caret>\"></div></section>" +
                 "</body>",
                 "name", "nickname", "photo", "title", "role", "url", "affiliation", "friend", "acquaintance", "address"
    );
  }

  public void testPropValueNestedScopes() {
    final VirtualFile personFile = myFixture.copyFileToProject("Person.html");
    final VirtualFile addressFile = myFixture.copyFileToProject("Address.html");
    ApplicationManager.getApplication().runWriteAction(() -> {
      ExternalResourceManager.getInstance().addResource("http://data-vocabulary.org/Person", personFile.getPath());
      ExternalResourceManager.getInstance().addResource("http://data-vocabulary.org/Address", addressFile.getPath());
    });
    doTestInHtml("<div itemscope itemtype=\"http://data-vocabulary.org/Person\">\n" +
                 "    My name is <span itemprop=\"name\">Smith</span>\n" +
                 "    <span itemprop=\"<caret>\" itemscope itemtype=\"http://data-vocabulary.org/Address\">\n" +
                 "        <span itemprop=\"locality\">Albuquerque</span>\n" +
                 "        <span itemprop=\"region\">NM</span>\n" +
                 "    </span>\n" +
                 "</div>",
                 "name", "nickname", "photo", "title", "role", "url", "affiliation", "friend", "acquaintance", "address"
    );
  }

  public void testPropValueNestedScopesDifferentTrees() {
    final VirtualFile personFile = myFixture.copyFileToProject("Person.html");
    final VirtualFile addressFile = myFixture.copyFileToProject("Address.html");
    ApplicationManager.getApplication().runWriteAction(() -> {
      ExternalResourceManager.getInstance().addResource("http://data-vocabulary.org/Person", personFile.getPath());
      ExternalResourceManager.getInstance().addResource("http://data-vocabulary.org/Address", addressFile.getPath());
    });
    doTestInHtml("<div itemscope itemtype=\"http://data-vocabulary.org/Person\" >\n" +
                 "    name is <span itemprop=\"name\">ann</span>\n" +
                 "    role is <span itemprop=\"role\">smth</span>\n" +
                 "   <span itemprop=\"address\" itemscope\n" +
                 "         itemtype=\"http://data-vocabulary.org/Address\" itemref=\"qq\">\n" +
                 "      <span itemprop=\"locality\">spb</span>\n" +
                 "   </span>\n" +
                 "</div>\n" +
                 "<div>\n" +
                 "    <span id=\"qq\" itemprop=\"<caret>\">russia</span>\n" +
                 "</div>",
                 "street-address", "locality", "region", "postal-code", "country-name"
    );
  }
}
