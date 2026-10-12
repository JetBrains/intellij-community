/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package com.intellij.xml.structureView;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.smartTree.TreeStructureUtil;
import com.intellij.lang.html.structureView.Html5SectionsNodeProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.FileStructureTestFixture;
import com.intellij.testFramework.FileStructureTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class HtmlFileStructureTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private CodeInsightTestFixture myFixture;
  private FileStructureTestFixture myPopupFixture;
  private String myTestMethodName;
  private boolean myHtml5OutlineModeDefault;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(PlatformTestUtil.getCommunityPath() + "/xml/tests/testData/structureView/");
    myPopupFixture = new FileStructureTestFixture(myFixture);
    myTestMethodName = testInfo.getTestMethod().orElseThrow().getName();
    myHtml5OutlineModeDefault = PropertiesComponent.getInstance().getBoolean(getHtml5OutlineModePropertyName());
  }

  @AfterEach
  void tearDown() {
    try {
      PropertiesComponent.getInstance().setValue(getHtml5OutlineModePropertyName(), myHtml5OutlineModeDefault);
    }
    finally {
      EdtTestUtil.runInEdtAndWait(() -> Disposer.dispose(myPopupFixture));
    }
  }

  private static String getHtml5OutlineModePropertyName() {
    return TreeStructureUtil.getPropertyName(Html5SectionsNodeProvider.HTML5_OUTLINE_PROVIDER_PROPERTY);
  }

  private void checkTree() {
    FileStructureTestUtil.configureDefault(myFixture, myTestMethodName, "html");
    EdtTestUtil.runInEdtAndWait(() -> {
      setHtml5OutlineMode(true);
      FileStructureTestUtil.checkConfiguredTree(myFixture, myPopupFixture, myTestMethodName, null, true);
    });
  }

  public void setHtml5OutlineMode(boolean enabled) {
    PlatformTestUtil.waitForPromise(myPopupFixture.getPopup().setTreeActionState(Html5SectionsNodeProvider.ACTION_ID, enabled));
    myPopupFixture.updateAndSelectCurrent();
  }

  @Test public void testEmpty() {checkTree();}
  @Test public void testSimple() {checkTree();}
  @Test public void testNoSectioningRoot() {checkTree();}
  @Test public void testImplicitSections() {checkTree();}
  @Test public void testMultipleRootTags() {checkTree();}
}
