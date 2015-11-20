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
import com.jetbrains.python.documentation.docstrings.*;
import com.jetbrains.python.documentation.docstrings.SectionBasedDocString.Section;
import com.jetbrains.python.documentation.docstrings.SectionBasedDocString.SectionField;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PySectionBasedDocStringTest extends PyTestCase {

  public void testSimpleGoogleDocString() {
    checkSimpleDocstringStructure(findAndParseGoogleStyleDocString());
  }

  public void testSimpleNumpyDocstring() {
    checkSimpleDocstringStructure(findAndParseNumpyStyleDocString());
  }

  private static void checkSimpleDocstringStructure(@NotNull SectionBasedDocString docString) {
    assertEquals("Summary", docString.getSummary());
    final List<Section> sections = docString.getSections();
    assertSize(3, sections);

    assertEquals("parameters", sections.get(0).getNormalizedTitle());
    final List<SectionField> paramFields = sections.get(0).getFields();
    assertSize(2, paramFields);
    final SectionField param1 = paramFields.get(0);
    assertEquals("x", param1.getName());
    assertEquals("int", param1.getType());
    assertEquals("first parameter", param1.getDescription());

    final SectionField param2 = paramFields.get(1);
    assertEquals("y", param2.getName());
    assertEmpty(param2.getType());
    assertEquals("second parameter\n" +
                 "with longer description", param2.getDescription());

    assertEquals("raises", sections.get(1).getNormalizedTitle());
    final List<SectionField> exceptionFields = sections.get(1).getFields();
    assertSize(1, exceptionFields);
    final SectionField exception1 = exceptionFields.get(0);
    assertEmpty(exception1.getName());
    assertEquals("Exception", exception1.getType());
    assertEquals("if anything bad happens", exception1.getDescription());

    assertEquals("returns", sections.get(2).getNormalizedTitle());
    final List<SectionField> returnFields = sections.get(2).getFields();
    assertSize(1, returnFields);
    final SectionField return1 = returnFields.get(0);
    assertEmpty(return1.getName());
    assertEquals("None", return1.getType());
    assertEquals("always", return1.getDescription());
  }

  @NotNull
  private Substring findAndParseDocString() {
    myFixture.configureByFile(getTestName(true) + ".py");
    final String docStringText = findFirstDocString();

    assertNotNull(docStringText);
    return new Substring(docStringText);
  }

  @NotNull
  private GoogleCodeStyleDocString findAndParseGoogleStyleDocString() {
    return new GoogleCodeStyleDocString(findAndParseDocString());
  }

  @NotNull
  private NumpyDocString findAndParseNumpyStyleDocString() {
    return new NumpyDocString(findAndParseDocString());
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

  public void testSectionStartAfterQuotes() {
    final GoogleCodeStyleDocString docString = findAndParseGoogleStyleDocString();
    assertEmpty(docString.getSummary());

    assertSize(2, docString.getSections());

    final Section examplesSection = docString.getSections().get(0);
    assertEquals("examples", examplesSection.getNormalizedTitle());
    assertSize(1, examplesSection.getFields());
    final SectionField example1 = examplesSection.getFields().get(0);
    assertEmpty(example1.getName());
    assertEmpty(example1.getType());
    assertEquals("Useless call\n" +
                 "func() == func()", example1.getDescription());

    final Section notesSection = docString.getSections().get(1);
    assertEquals("notes", notesSection.getNormalizedTitle());
    assertSize(1, notesSection.getFields());
    final SectionField note1 = notesSection.getFields().get(0);
    assertEmpty(note1.getName());
    assertEmpty(note1.getType());
    assertEquals("some\n" +
                 "notes", note1.getDescription());
  }

  public void testTypeReferences() {
    final GoogleCodeStyleDocString docString = findAndParseGoogleStyleDocString();
    assertEmpty(docString.getSummary());
    assertSize(2, docString.getSections());

    final Section paramSection = docString.getSections().get(0);
    assertSize(1, paramSection.getFields());
    final SectionField param1 = paramSection.getFields().get(0);
    assertEquals("a1", param1.getName());
    assertEquals(":class:`MyClass`", param1.getType());
    assertEquals("used to call :def:`my_function` and access :attr:`my_attr`", param1.getDescription());

    final Section raisesSection = docString.getSections().get(1);
    assertSize(1, raisesSection.getFields());
    final SectionField exception1 = raisesSection.getFields().get(0);
    assertEmpty(exception1.getName());
    assertEquals(":class:`MyException`", exception1.getType());
    assertEquals("thrown in case of any error", exception1.getDescription());
  }

  public void testNestedIndentation() {
    final GoogleCodeStyleDocString docString = findAndParseGoogleStyleDocString();
    assertSize(1, docString.getSections());
    final Section section1 = docString.getSections().get(0);
    assertEquals("parameters", section1.getNormalizedTitle());
    assertSize(1, section1.getFields());
    final SectionField param1 = section1.getFields().get(0);
    assertEquals("x", param1.getName());
    assertEquals("int", param1.getType());
    assertEquals("first line of the description\n" +
                 "second line\n" +
                 "  third line\n" +
                 "\n" +
                 "Example::\n" +
                 "\n" +
                 "    assert func(42) is None", param1.getDescription());
  }

  public void testMultilineSummary() {
    final GoogleCodeStyleDocString docString = findAndParseGoogleStyleDocString();
    assertEquals("First line\n" +
                 "Second line\n" +
                 "Third line", docString.getSummary());
  }

  public void testNamedReturnsAndYields() {
    final GoogleCodeStyleDocString docString = findAndParseGoogleStyleDocString();
    assertEmpty(docString.getSummary());
    assertSize(2, docString.getSections());

    final Section returnSection = docString.getSections().get(0);
    assertSize(2, returnSection.getFields());

    final SectionField return1 = returnSection.getFields().get(0);
    assertEquals("status_code", return1.getName());
    assertEquals("int", return1.getType());
    assertEquals("HTTP status code", return1.getDescription());

    final SectionField return2 = returnSection.getFields().get(1);
    assertEquals("template", return2.getName());
    assertEquals("str", return2.getType());
    assertEquals("path to template in template roots", return2.getDescription());

    final Section yieldSection = docString.getSections().get(1);
    assertSize(1, yieldSection.getFields());
    final SectionField yield1 = yieldSection.getFields().get(0);
    assertEquals("progress", yield1.getName());
    assertEquals("float", yield1.getType());
    assertEquals("floating point value in range [0, 1) indicating progress\n" +
                 "of the task", yield1.getDescription());
  }

  public void testNumpySignature() {
    final NumpyDocString docString = findAndParseNumpyStyleDocString();
    assertEquals("a.diagonal(offset=0, axis1=0, axis2=1)", docString.getSignature());
    assertEquals("Return specified diagonals.", docString.getSummary());
  }

  public void testNumpySectionBlockBreaksOnDoubleEmptyLine() {
    final NumpyDocString docString = findAndParseNumpyStyleDocString();
    assertSize(1, docString.getSections());
    final Section paramSection = docString.getSections().get(0);
    assertEquals("parameters", paramSection.getNormalizedTitle());
    assertSize(1, paramSection.getFields());
    final SectionField param1 = paramSection.getFields().get(0);
    assertEquals("x", param1.getName());
    assertEmpty(param1.getType());
    assertEquals("First line\n" +
                 "Second line\n" +
                 "\n" +
                 "Line after single break", param1.getDescription());
  }

  public void testGoogleEmptyParamTypeInParenthesis() {
    final GoogleCodeStyleDocString docString = findAndParseGoogleStyleDocString();
    assertSize(1, docString.getSections());
    final Section paramSection = docString.getSections().get(0);
    assertEquals("parameters", paramSection.getNormalizedTitle());
    assertSize(1, paramSection.getFields());
    final SectionField param1 = paramSection.getFields().get(0);
    assertEquals("x", param1.getName());
    assertEmpty(param1.getDescription());
    assertEmpty(param1.getType());
    assertNotNull(param1.getTypeAsSubstring());
    assertEquals(26, param1.getTypeAsSubstring().getStartOffset());
    assertEquals(26, param1.getTypeAsSubstring().getEndOffset());
  }

  public void testGoogleReturnTypeNoDescription() {
    final GoogleCodeStyleDocString docString = findAndParseGoogleStyleDocString();
    assertSize(1, docString.getSections());
    final Section returnSection = docString.getSections().get(0);
    assertEquals("returns", returnSection.getNormalizedTitle());
    assertSize(1, returnSection.getFields());
    final SectionField return1 = returnSection.getFields().get(0);
    assertEmpty(return1.getName());
    assertEmpty(return1.getDescription());
    assertEquals("object", return1.getType());
    assertNotNull(return1.getTypeAsSubstring());
    assertEquals(20, return1.getTypeAsSubstring().getStartOffset());
    assertEquals(26, return1.getTypeAsSubstring().getEndOffset());
  }

  public void testGoogleNoEmptyLineAfterSummary() {
    final GoogleCodeStyleDocString docString = findAndParseGoogleStyleDocString();
    assertEquals("Summary.", docString.getSummary());
    assertSize(1, docString.getSections());
    assertSize(1, docString.getSections().get(0).getFields());
  }

  public void testGoogleParametersSectionWithoutSummary() {
    final GoogleCodeStyleDocString docString = findAndParseGoogleStyleDocString();
    assertEmpty(docString.getSummary());
    assertSize(1, docString.getSections());
    final Section paramSection = docString.getSections().get(0);
    assertEquals("parameters", paramSection.getNormalizedTitle());
    assertSize(1, paramSection.getFields());
  }

  public void testGoogleKeywordArgumentsSection() {
    final GoogleCodeStyleDocString docString = findAndParseGoogleStyleDocString();
    assertEmpty(docString.getSummary());
    assertSize(1, docString.getSections());
    assertEquals("keyword arguments", docString.getSections().get(0).getNormalizedTitle());
  }

  // PY-16766
  public void testGoogleDocStringContentDetection() {
    assertTrue(DocStringUtil.isLikeGoogleDocString(
      "\n" +
      "    My Section:\n" +
      "        some user defined section\n" +
      "    \n" +
      "    Parameters:\n" +
      "        param1: \n" +
      "\n" +
      "    Returns:\n"));
  }

  public void testNumpyEmptySectionIndent() {
    final NumpyDocString docString = findAndParseNumpyStyleDocString();
    assertSize(3, docString.getSections());
    final Section paramSection = docString.getSections().get(0);
    assertEquals("parameters", paramSection.getNormalizedTitle());
    assertSize(2, paramSection.getFields());
    final Section exampleSection = docString.getSections().get(1);
    assertSize(1, exampleSection.getFields());
    assertEquals("First sentence.\n" +
                 "Second sentence.", exampleSection.getFields().get(0).getDescription());
    final Section returnSection = docString.getSections().get(2);
    assertSize(1, returnSection.getFields());
    assertEquals("Something", returnSection.getFields().get(0).getType());
  }

  public void testGoogleParamNamedLikeSection() {
    final GoogleCodeStyleDocString docString = findAndParseGoogleStyleDocString();
    assertSize(1, docString.getSections());
    final Section paramSection = docString.getSections().get(0);
    assertSize(2, paramSection.getFields());
    assertEquals("x", paramSection.getFields().get(0).getName());
    assertEquals("args", paramSection.getFields().get(1).getName());
  }

  public void testGoogleNoColonAfterParameter() {
    final GoogleCodeStyleDocString docString = findAndParseGoogleStyleDocString();
    assertSize(1, docString.getSections());
    final Section paramSection = docString.getSections().get(0);
    assertSize(2, paramSection.getFields());
    final SectionField x = paramSection.getFields().get(0);
    assertEquals("x", x.getName());
    assertEmpty(x.getType());
    assertEmpty(x.getDescription());

    final SectionField y = paramSection.getFields().get(1);
    assertEquals("y", y.getName());
    assertEquals("int", y.getType());
    assertEmpty(y.getDescription());
  }

  public void testNumpyMultipleReturns() {
    final NumpyDocString docString = findAndParseNumpyStyleDocString();
    assertSize(1, docString.getSections());
    final Section returnSection = docString.getSections().get(0);
    assertSize(2, returnSection.getFields());
  }

  // PY-16908
  public void testNumpyCombinedParamDeclarations() {
    final NumpyDocString docString = findAndParseNumpyStyleDocString();
    assertSize(1, docString.getSections());
    final Section paramSection = docString.getSections().get(0);
    assertSize(1, paramSection.getFields());
    final SectionField firstField = paramSection.getFields().get(0);
    assertSameElements(firstField.getNames(), "x", "y", "args", "kwargs");
    assertEquals(firstField.getType(), "Any");
    assertEquals(firstField.getDescription(), "description");
  }

  // PY-16991
  public void testGoogleMandatoryIndentationInsideSection() {
    final GoogleCodeStyleDocString docString = findAndParseGoogleStyleDocString();
    assertSize(3, docString.getSections());
    assertEmpty(docString.getSections().get(0).getFields());
    assertSize(1, docString.getSections().get(1).getFields());
    final Section thirdSection = docString.getSections().get(2);
    assertSize(1, thirdSection.getFields());
    final SectionField firstExample = thirdSection.getFields().get(0);
    assertEmpty(firstExample.getName());
    assertEmpty(firstExample.getType());
    assertEquals("first line\n" +
                 "second line", firstExample.getDescription());
  }

  // PY-17002
  public void testGoogleNoClosingParenthesisAfterParamType() {
    final GoogleCodeStyleDocString docString = findAndParseGoogleStyleDocString();
    assertSize(1, docString.getSections());
    final List<SectionField> params = docString.getSections().get(0).getFields();
    assertSize(2, params);
    assertEquals("Foo", params.get(0).getType());
    assertEquals("Bar", params.get(1).getType());
  }

  // PY-17657, PY-16303
  public void testNotGoogleFormatIfDocstringContainTags() {
    assertEquals(DocStringFormat.REST, DocStringUtil.guessDocStringFormat("\"\"\"\n" +
                                                                          ":type sub_field: FieldDescriptor | () -> FieldDescriptor\n" +
                                                                          ":param sub_field: The type of field in this collection\n" +
                                                                          "    Tip: You can pass a ValueObject class here to ...\n" +
                                                                          "    Example:\n" +
                                                                          "        addresses = field.Collection(AddressObject)\n" +
                                                                          "\"\"\""));
    
    assertEquals(DocStringFormat.REST, DocStringUtil.guessDocStringFormat("\"\"\"\n" +
                                                                          "Args:\n" +
                                                                          "    :param Tuple[int, int] name: Some description\n" +
                                                                          "\"\"\""));
    
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/docstrings";
  }
}
