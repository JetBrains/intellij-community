// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class Py3QuickDocTest extends LightMarkedTestCase {
  private PythonDocumentationProvider myProvider;
  private DocStringFormat myFormat;

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPyLatestDescriptor;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // the provider is stateless, can be reused, as in real life
    myProvider = new PythonDocumentationProvider();
    final PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    myFormat = documentationSettings.getFormat();
    documentationSettings.setFormat(DocStringFormat.PLAIN);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      final PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
      documentationSettings.setFormat(myFormat);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private void checkByHTML(@NotNull String text) {
    assertSameLinesWithFile(getTestDataPath() + getTestName(false) + ".html", text);
  }

  @Override
  protected Map<String, PsiElement> loadTest() {
    return configureByFile(getTestName(false) + ".py");
  }

  protected void checkHTMLOnly() {
    final Map<String, PsiElement> marks = loadTest();
    final PsiElement originalElement = marks.get("<the_ref>");
    assertNotNull("<the_ref> marker is missing in test data", originalElement);
    final DocumentationManager manager = DocumentationManager.getInstance(myFixture.getProject());
    final PsiElement target = manager.findTargetElement(myFixture.getEditor(),
                                                        originalElement.getTextOffset(),
                                                        myFixture.getFile(),
                                                        originalElement);
    checkByHTML(myProvider.generateDoc(target, originalElement));
  }

  // PY-49935
  public void testConcatenateInReturn() {
    checkHTMLOnly();
  }

  // PY-49935
  public void testConcatenateInParam() {
    checkHTMLOnly();
  }

  // PY-49935
  public void testConcatenateSeveralFirstParamInParam() {
    checkHTMLOnly();
  }

  // PY-49935
  public void testConcatenateInGeneric() {
    checkHTMLOnly();
  }

  public void testSeveralParamSpecs() {
    checkHTMLOnly();
  }

  // PY-53104
  public void testSelf() {
    checkHTMLOnly();
  }

  // PY-53104
  public void testTypingExtensionsSelf() {
    checkHTMLOnly();
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/quickdoc/";
  }
}
