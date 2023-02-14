/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.relaxNG;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.relaxNG.inspections.RngDomInspection;
import org.intellij.plugins.testUtil.IdeaCodeInsightTestCase;
import org.intellij.plugins.testUtil.ResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class HighlightingTestBase extends UsefulTestCase implements IdeaCodeInsightTestCase {
  protected CodeInsightTestFixture myTestFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();

    myTestFixture = createFixture(factory);

    myTestFixture.setTestDataPath(getTestDataBasePath() + getTestDataPath());

    List<Class<? extends LocalInspectionTool>> inspectionClasses = Arrays.asList(new DefaultInspectionProvider().getInspectionClasses());
    if (getName().contains("Inspection")) {
      inspectionClasses = ContainerUtil.concat(inspectionClasses, RelaxNgMetaDataContributor.getInspectionClasses());
    }

    myTestFixture.setUp();

    myTestFixture.enableInspections(inspectionClasses);

    WriteAction.runAndWait(() -> {
      ResourceUtil.copyFiles(this);
      init();
    });
  }

  protected static String toAbsolutePath(String relativeTestDataPath) {
    return FileUtil.toSystemDependentName(getTestDataBasePath() + relativeTestDataPath);
  }

  public static String getTestDataBasePath() {
    return PlatformTestUtil.getCommunityPath() + "/xml/relaxng/testData/";
  }

  protected CodeInsightTestFixture createFixture(@NotNull IdeaTestFixtureFactory factory) {
    final TestFixtureBuilder<IdeaProjectTestFixture> builder = factory.createLightFixtureBuilder(getTestName(false));
    final IdeaProjectTestFixture fixture = builder.getFixture();

    return factory.createCodeInsightFixture(fixture);
  }

  protected CodeInsightTestFixture createContentFixture(IdeaTestFixtureFactory factory) {
    TestFixtureBuilder<IdeaProjectTestFixture> builder = factory.createFixtureBuilder(getName());
    EmptyModuleFixtureBuilder<?> moduleBuilder = builder.addModule(EmptyModuleFixtureBuilder.class);
    IdeaProjectTestFixture fixture = builder.getFixture();

    CodeInsightTestFixture testFixture = factory.createCodeInsightFixture(fixture);

    moduleBuilder.addContentRoot(testFixture.getTempDirPath());
    moduleBuilder.addSourceRoot("/");

    return testFixture;
  }

  @Override
  public CodeInsightTestFixture getFixture() {
    return myTestFixture;
  }

  @Override
  public abstract String getTestDataPath();

  protected void init() {
    ExternalResourceManagerEx.getInstanceEx().addIgnoredResources(Collections.singletonList("urn:test:undefined"), getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myTestFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myTestFixture = null;
      super.tearDown();
    }
  }

  protected void doHighlightingTest(String s) {
    doCustomHighlighting(s, true, false);
//    myTestFixture.testHighlighting(true, false, true, s);
  }

  protected void doExternalToolHighlighting(String name) {
    doCustomHighlighting(name, true, true);
  }

  protected void doCustomHighlighting(String name, final boolean checkWeakWarnings, final Boolean includeExternalToolPass) {
    myTestFixture.configureByFile(name);

    doCustomHighlighting(checkWeakWarnings, includeExternalToolPass);
  }

  protected void doCustomHighlighting(boolean checkWeakWarnings, Boolean includeExternalToolPass) {
    final PsiFile file = myTestFixture.getFile();
    final Document doc = myTestFixture.getEditor().getDocument();
    ExpectedHighlightingData data = new ExpectedHighlightingData(doc, true, checkWeakWarnings, false);
    data.init();
    PsiDocumentManager.getInstance(myTestFixture.getProject()).commitAllDocuments();

    Collection<HighlightInfo> highlights1 = doHighlighting(includeExternalToolPass);

    data.checkResult(file, highlights1, doc.getText());
  }

  @NotNull
  protected Collection<HighlightInfo> doHighlighting(final Boolean externalToolPass) {
    final Project project = myTestFixture.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final Editor editor = myTestFixture.getEditor();

    int[] ignore = externalToolPass == null || externalToolPass ? new int[]{
      Pass.LINE_MARKERS,
      Pass.LOCAL_INSPECTIONS,
      Pass.SLOW_LINE_MARKERS,
      Pass.POPUP_HINTS,
      Pass.UPDATE_ALL,
      Pass.UPDATE_FOLDING,
    } : new int[]{Pass.EXTERNAL_TOOLS};
    return CodeInsightTestFixtureImpl.instantiateAndRun(myTestFixture.getFile(), editor, ignore, false);
  }

  protected void doTestCompletion(String name, String ext) {
    myTestFixture.testCompletion(name + "." + ext, name + "_after." + ext);
  }

  protected void doTestCompletion(String before, String... variants) {
    myTestFixture.testCompletionVariants(before, variants);
  }

  protected void doTestCompletion(String before) {
    doTestCompletion(before, "xml");
  }

  protected void doTestRename(String name, String ext, String newName) {
    myTestFixture.testRename(name + "." + ext, name + "_after." + ext, newName);
  }

  @SuppressWarnings("deprecation")
  protected void doTestQuickFix(String file, String ext) {
    final PsiReference psiReference = myTestFixture.getReferenceAtCaretPositionWithAssertion(file + "." + ext);
    assertNull("Reference", psiReference.resolve());
    assertTrue(psiReference.getClass().getName() + " is not a QuickFixProvider", psiReference instanceof LocalQuickFixProvider);

    final LocalQuickFix[] fixes = ((LocalQuickFixProvider)psiReference).getQuickFixes();

    assertTrue("One action expected", fixes != null && fixes.length == 1);

    final Project project = myTestFixture.getProject();
    final ProblemDescriptor problemDescriptor = InspectionManager.getInstance(project).createProblemDescriptor(psiReference.getElement(),
                                                                                                               "Foo",
                                                                                                               fixes,
                                                                                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                                                               true);
    WriteCommandAction.writeCommandAction(project, myTestFixture.getFile()).run(() -> fixes[0].applyFix(project, problemDescriptor));
    myTestFixture.checkResultByFile(file + "_after." + ext);
  }

  private static class DefaultInspectionProvider implements InspectionToolProvider {
    @Override
    public Class<? extends LocalInspectionTool> @NotNull [] getInspectionClasses() {
      //noinspection unchecked
      return new Class[]{RngDomInspection.class, RequiredAttributesInspection.class};
    }
  }
}
