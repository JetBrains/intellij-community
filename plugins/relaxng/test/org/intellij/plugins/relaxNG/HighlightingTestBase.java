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

import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import junit.framework.TestCase;
import org.intellij.plugins.relaxNG.inspections.RngDomInspection;
import org.intellij.plugins.testUtil.IdeaCodeInsightTestCase;
import org.intellij.plugins.testUtil.ResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static com.intellij.openapi.util.io.FileUtil.delete;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 25.07.2007
 */
public abstract class HighlightingTestBase extends TestCase implements IdeaCodeInsightTestCase {
  private static final File ourTempPath;

  private static final String JAVA_IO_TMPDIR = "java.io.tmpdir";

  static {
    // IDEA leaves bazillions of "ideaXXXX.ipr" files and "unitTestXXXX.tmp" directories in the temp directory
    ourTempPath = new File(new File(System.getProperty(JAVA_IO_TMPDIR, "/tmp")), "test" + System.identityHashCode(Object.class));
    ourTempPath.mkdirs();

    try {
      System.setProperty(JAVA_IO_TMPDIR, ourTempPath.getCanonicalPath());
      Runtime.getRuntime().addShutdownHook(new Thread("Cleanup Temp") {
        @Override
        public void run() {
          delete(ourTempPath);
        }
      });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected CodeInsightTestFixture myTestFixture;

  protected void setUp() throws Exception {
    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();

    myTestFixture = createFixture(factory);

    myTestFixture.setTestDataPath(getTestDataBasePath() + getTestDataPath());

    InspectionToolProvider[] loaders;
    if (getName().contains("Inspection")) {
      loaders = new InspectionToolProvider[]{
              new ApplicationLoader(),
              new DefaultInspectionProvider()
      };
    } else {
      loaders = new InspectionToolProvider[]{
              new DefaultInspectionProvider()
      };
    }

    myTestFixture.setUp();

    myTestFixture.enableInspections(loaders);

    new WriteAction() {
      protected void run(Result result) throws Throwable {
        ResourceUtil.copyFiles(HighlightingTestBase.this);
        init();
      }
    }.execute();
  }

  protected static String toAbsolutePath(String relativeTestDataPath) {
    return FileUtil.toSystemDependentName(getTestDataBasePath() + relativeTestDataPath);
  }

  public static String getTestDataBasePath() {
    return PluginPathManager.getPluginHomePath("relaxng") + "/testData/";
  }

  protected CodeInsightTestFixture createFixture(IdeaTestFixtureFactory factory) {
    final TestFixtureBuilder<IdeaProjectTestFixture> builder = factory.createLightFixtureBuilder();
    final IdeaProjectTestFixture fixture = builder.getFixture();

    final CodeInsightTestFixture testFixture;
    testFixture = factory.createCodeInsightFixture(fixture);

    return testFixture;
  }

  protected CodeInsightTestFixture createContentFixture(IdeaTestFixtureFactory factory) {
    final TestFixtureBuilder<IdeaProjectTestFixture> builder = factory.createFixtureBuilder();
    final EmptyModuleFixtureBuilder moduleBuilder = builder.addModule(EmptyModuleFixtureBuilder.class);
    final IdeaProjectTestFixture fixture = builder.getFixture();

    final CodeInsightTestFixture testFixture;
    testFixture = factory.createCodeInsightFixture(fixture);

    final String root = testFixture.getTempDirPath();
    moduleBuilder.addContentRoot(root);
    moduleBuilder.addSourceRoot("/");

    return testFixture;
  }

  public CodeInsightTestFixture getFixture() {
    return myTestFixture;
  }

  public abstract String getTestDataPath();

  protected void init() {
    ExternalResourceManagerEx.getInstanceEx().addIgnoredResource("urn:test:undefined");
  }

  protected void tearDown() throws Exception {
    myTestFixture.tearDown();
  }

  protected void doHighlightingTest(String s) throws Throwable {
    doCustomHighlighting(s, true, false);
//    myTestFixture.testHighlighting(true, false, true, s);
  }

  protected void doExternalToolHighlighting(String name) throws Throwable {
    doCustomHighlighting(name, true, true);
  }

  protected void doCustomHighlighting(String name, final boolean checkWeakWarnings, final Boolean includeExternalToolPass) throws Throwable {
    myTestFixture.configureByFile(name);

    final PsiFile file = myTestFixture.getFile();
    new WriteCommandAction(myTestFixture.getProject(), file) {
      protected void run(Result result) throws Throwable {
        final Document doc = myTestFixture.getEditor().getDocument();
        ExpectedHighlightingData data = new ExpectedHighlightingData(doc, true, checkWeakWarnings, false, file);
        PsiDocumentManager.getInstance(myTestFixture.getProject()).commitAllDocuments();

        Collection<HighlightInfo> highlights1 = doHighlighting(includeExternalToolPass);

        data.checkResult(highlights1, doc.getText());
      }
    }.execute();
  }

  @NotNull
  private Collection<HighlightInfo> doHighlighting(final Boolean externalToolPass) {
    final Project project = myTestFixture.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final Editor editor = myTestFixture.getEditor();

    int[] ignore = externalToolPass == null || externalToolPass ? new int[]{
      com.intellij.codeHighlighting.Pass.LINE_MARKERS,
      com.intellij.codeHighlighting.Pass.LOCAL_INSPECTIONS,
      com.intellij.codeHighlighting.Pass.POPUP_HINTS,
      com.intellij.codeHighlighting.Pass.POST_UPDATE_ALL,
      com.intellij.codeHighlighting.Pass.UPDATE_ALL,
      com.intellij.codeHighlighting.Pass.UPDATE_FOLDING,
      com.intellij.codeHighlighting.Pass.UPDATE_OVERRIDEN_MARKERS,
      com.intellij.codeHighlighting.Pass.VISIBLE_LINE_MARKERS,
    } : new int[]{com.intellij.codeHighlighting.Pass.EXTERNAL_TOOLS};
    return CodeInsightTestFixtureImpl.instantiateAndRun(myTestFixture.getFile(), editor, ignore, false);
  }

  protected void doTestCompletion(String name, String ext) throws Throwable {
    myTestFixture.testCompletion(name + "." + ext, name + "_after." + ext);
  }

  protected void doTestCompletion(String before, String... variants) throws Throwable {
    myTestFixture.testCompletionVariants(before, variants);
  }

  protected void doTestCompletion(String before) throws Throwable {
    doTestCompletion(before, "xml");
  }

  protected void doTestRename(String name, String ext, String newName) throws Throwable {
    myTestFixture.testRename(name + "." + ext, name + "_after." + ext, newName);
  }

  @SuppressWarnings({ "deprecation", "unchecked" })
  protected void doTestQuickFix(String file, String ext) throws Throwable {
    final PsiReference psiReference = myTestFixture.getReferenceAtCaretPositionWithAssertion(file + "." + ext);
    assertNull("Reference", psiReference.resolve());
    assertTrue("QuickFixProvider", psiReference instanceof QuickFixProvider);

    final HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, 0, 0, "");
    ((QuickFixProvider)psiReference).registerQuickfix(info, psiReference);
    assertTrue("One action expected", info.quickFixActionRanges.size() == 1);

    final Pair<HighlightInfo.IntentionActionDescriptor, TextRange> rangePair = info.quickFixActionRanges.get(0);
    final IntentionAction action = rangePair.first.getAction();

    assertTrue("action is enabled", action.isAvailable(myTestFixture.getProject(), myTestFixture.getEditor(), myTestFixture.getFile()));
    myTestFixture.launchAction(action);

    myTestFixture.checkResultByFile(file + "_after." + ext);
  }

  private static class DefaultInspectionProvider implements InspectionToolProvider {
    public Class[] getInspectionClasses() {
      return new Class[]{
              RngDomInspection.class,
              RequiredAttributesInspection.class
      };
    }
  }

  private static class MyMockProgressIndicator extends MockProgressIndicator implements UserDataHolder {
    private final UserDataHolderBase myHolder = new UserDataHolderBase();

    public <T> T getUserData(@NotNull Key<T> key) {
      return myHolder.getUserData(key);
    }

    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
      myHolder.putUserData(key, value);
    }
  }
}
