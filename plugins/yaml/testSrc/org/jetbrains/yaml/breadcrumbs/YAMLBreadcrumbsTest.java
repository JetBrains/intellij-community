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
package org.jetbrains.yaml.breadcrumbs;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.breadcrumbs.BreadcrumbsInfoProvider;
import com.intellij.xml.breadcrumbs.BreadcrumbsItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLFileType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class YAMLBreadcrumbsTest extends LightPlatformCodeInsightFixtureTestCase {

  private static final String INPUT = "---\n" +
                                      "items:\n" +
                                      "    - part_no:   A4786\n" +
                                      "      descrip:   Water Bucket (Filled)\n" +
                                      "      price:     1.47\n" +
                                      "      quantity:  4\n" +
                                      "\n" +
                                      "    - part_no:   E1628\n" +
                                      "      descrip:   High Heeled<caret> \"Ruby\" Slippers\n" +
                                      "      size:      8\n" +
                                      "      price:     133.7\n" +
                                      "      quantity:  1\n" +
                                      "\n" +
                                      "specialDelivery:  >\n" +
                                      "    Follow the Yellow Brick\n" +
                                      "    Road to the Emerald City.\n" +
                                      "    Pay no attention<caret> to the\n" +
                                      "    man behind the curtain.\n" +
                                      "---\n" +
                                      "{\n" +
                                      "foo: salkdjkalsd,\n" +
                                      "bar: asjdjkas,\n" +
                                      "baz: [foo: qoo, boo: fo<caret>o, doo: 123]\n" +
                                      "}\n" +
                                      "---\n" +
                                      "foo: \n" +
                                      "  bar:\n" +
                                      "- av<caret>r\n" +
                                      "...";
  private static final String OUTPUT = "[Document 1/3;null][items:;null][Item 2/2;null][descrip:;null][High Heeled \"Ruby\" S...;null]\n" +
                                       "------\n" +
                                       "[Document 1/3;null][specialDelivery:;null][Follow the Yellow Br...;null]\n" +
                                       "------\n" +
                                       "[Document 2/3;null][baz:;null][Item 2/3;null][boo:;null][foo;null]\n" +
                                       "------\n" +
                                       "[Document 3/3;null][foo:;null][Item;null][avr;null]";

  public void testAll() {
    myFixture.configureByText(YAMLFileType.YML, INPUT);
    final CaretModel caretModel = myFixture.getEditor().getCaretModel();
    final String result = caretModel.getAllCarets().stream()
      .map(Caret::getOffset)
      .collect(Collectors.toList()).stream()
      .map((offset) -> {
        caretModel.moveToOffset(offset);
        return getBreadcrumbs(myFixture).stream()
          .map(BreadcrumbsItem::toString)
          .reduce((left, right) -> right + left).orElse("[]");
      })
      .reduce((left, right) -> left + "\n------\n" + right).orElse("");

    assertSameLines(OUTPUT, result);
  }

  // TODO move this method and class to platform when breadcrumbs are moved there, too
  @NotNull
  private static List<BreadcrumbsItem> getBreadcrumbs(@NotNull CodeInsightTestFixture fixture) {
    PsiElement element = fixture.getFile().findElementAt(fixture.getCaretOffset());
    if (element == null) {
      return Collections.emptyList();
    }
    final Language language = element.getContainingFile().getLanguage();

    final BreadcrumbsInfoProvider provider = ContainerUtil.find(Extensions.getExtensions(BreadcrumbsInfoProvider.EP_NAME),
                                                                (p) -> Arrays.asList(p.getLanguages()).contains(language));
    if (provider == null) {
      return Collections.emptyList();
    }

    List<BreadcrumbsItem> result = new ArrayList<>();
    while (element != null) {
      if (provider.acceptElement(element)) {
        result.add(new MockBreadcrumbsItem(provider.getElementInfo(element), provider.getElementTooltip(element)));
      }
      element = provider.getParent(element);
    }
    return result;
  }

  private static class MockBreadcrumbsItem extends BreadcrumbsItem {
    @Nullable
    private final String myDisplayText;
    @Nullable
    private final String myTooltip;

    private MockBreadcrumbsItem(@Nullable String displayText, @Nullable String tooltip) {
      myDisplayText = displayText;
      myTooltip = tooltip;
    }

    @Nullable
    @Override
    public String getDisplayText() {
      return myDisplayText;
    }

    @Nullable
    @Override
    public String getTooltip() {
      return myTooltip;
    }

    @Override
    public String toString() {
      return "[" + getDisplayText() + ";" + getTooltip() + "]";
    }
  }
}
