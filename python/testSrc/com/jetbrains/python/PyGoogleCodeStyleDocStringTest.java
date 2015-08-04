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
package com.jetbrains.python;

import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.documentation.GoogleCodeStyleDocString;
import com.jetbrains.python.documentation.SectionBasedDocString.Section;
import com.jetbrains.python.documentation.SectionBasedDocString.SectionField;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyGoogleCodeStyleDocStringTest extends PyTestCase {
  
  public void testSimpleFunctionDocString() {
    myFixture.configureByFile(getTestName(true) + ".py");
    final String docStringText = findFirstDocString();
    
    assertNotNull(docStringText);
    final GoogleCodeStyleDocString docString = new GoogleCodeStyleDocString(docStringText);
    assertEquals("Summary", docString.getSummary());
    final List<Section> sections = docString.getSections();
    assertSize(3, sections);
    
    assertEquals("parameters", sections.get(0).getTitle());
    final List<SectionField> paramFields = sections.get(0).getFields();
    assertSize(2, paramFields);
    final SectionField firstParamField = paramFields.get(0);
    assertNotNull(firstParamField.getName());
    assertEquals("x", firstParamField.getName().toString());
    assertNotNull(firstParamField.getType());
    assertEquals("int", firstParamField.getType().toString());
    assertNotNull(firstParamField.getDescription());
    assertEquals("first parameter", firstParamField.getDescription().toString());

    final SectionField secondParamField = paramFields.get(1);
    assertNotNull(secondParamField.getName());
    assertEquals("y", secondParamField.getName().toString());
    assertNull(secondParamField.getType());
    assertNotNull(secondParamField.getDescription());
    assertEquals("second parameter\n" +
                 "        with longer description", secondParamField.getDescription().toString());

    assertEquals("raises", sections.get(1).getTitle());
    final List<SectionField> exceptionFields = sections.get(1).getFields();
    assertSize(1, exceptionFields);
    final SectionField firstExcField = exceptionFields.get(0);
    assertNull(firstExcField.getName());
    assertNotNull(firstExcField.getType());
    assertEquals("Exception", firstExcField.getType().toString());
    assertNotNull(firstExcField.getDescription());
    assertEquals("if anything bad happens", firstExcField.getDescription().toString());

    assertEquals("returns", sections.get(2).getTitle());
    final List<SectionField> returnFields = sections.get(2).getFields();
    assertSize(1, returnFields);
    final SectionField firstReturnField = returnFields.get(0);
    assertNull(firstReturnField.getName());
    assertNotNull(firstReturnField.getType());
    assertEquals("None", firstReturnField.getType().toString());
    assertNotNull(firstReturnField.getDescription());
    assertEquals("always", firstReturnField.getDescription().toString());
  }

  @Nullable
  private String findFirstDocString() {
    final PsiElementProcessor.FindElement<PsiElement> processor = new PsiElementProcessor.FindElement<PsiElement>() {
      @Override
      public boolean execute(@NotNull PsiElement element) {
        if (element instanceof PyStringLiteralExpression && element.getFirstChild().getNode().getElementType() == PyTokenTypes.DOCSTRING) {
          return setFound(element);
        }
        return true;
      }
    };
    PsiTreeUtil.processElements(myFixture.getFile(), processor);
    if (!processor.isFound()) {
      return null;
    }
    final PsiElement foundElement = processor.getFoundElement();
    assertNotNull(foundElement);
    return ((PyStringLiteralExpression)foundElement).getStringValue();
  } 

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/docstrings";
  }
}
