/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.lang.properties;

import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.ide.projectView.impl.GroupByTypeComparator;
import com.intellij.lang.properties.projectView.ResourceBundleGrouper;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.projectView.TestProjectTreeStructure;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

public class PropertiesProjectViewTest extends LightPlatformCodeInsightFixtureTestCase {
  private TestProjectTreeStructure myStructure;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myStructure = new TestProjectTreeStructure(getProject(), myTestRootDisposable);
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/testData/propertiesFile/projectView";
  }

  public void testBundle() {
    myFixture.copyDirectoryToProject(getTestName(true), getTestName(true));
    final AbstractProjectViewPSIPane pane = setupPane(true);

    String structure = "-Project\n" +
                       " -PsiDirectory: src\n" +
                       "  -PsiDirectory: bundle\n" +
                       "   yyy.properties\n" +
                       "   -Resource Bundle 'xxx'\n" +
                       "    xxx.properties\n" +
                       "    xxx_en.properties\n" +
                       "    xxx_ru_RU.properties\n" +
                       "   X.txt\n" +
                       " External Libraries\n";
    PlatformTestUtil.assertTreeEqual(pane.getTree(), structure);
  }

  public void testStandAlone() {
    myFixture.copyDirectoryToProject(getTestName(true), getTestName(true));
    final AbstractProjectViewPSIPane pane = setupPane(true);

    String structure = "-Project\n" +
                       " -PsiDirectory: src\n" +
                       "  -PsiDirectory: standAlone\n" +
                       "   a.properties\n" +
                       "   xxx.properties\n" +
                       "   xxx2.properties\n" +
                       "   yyy.properties\n" +
                       "   X.txt\n" +
                       " External Libraries\n";

    PlatformTestUtil.assertTreeEqual(pane.getTree(), structure);
  }

  public void testSortByType() {
    myFixture.copyDirectoryToProject(getTestName(true), getTestName(true));
    AbstractProjectViewPSIPane pane = setupPane(true);

    String structure = "-Project\n" +
                       " -PsiDirectory: src\n" +
                       "  -PsiDirectory: sortByType\n" +
                       "   a.properties\n" +
                       "   xxx2.properties\n" +
                       "   yyy.properties\n" +
                       "   -Resource Bundle 'xxx'\n" +
                       "    xxx.properties\n" +
                       "    xxx_en.properties\n" +
                       "   X.txt\n" +
                       " External Libraries\n";

    PlatformTestUtil.assertTreeEqual(pane.getTree(), structure);

    pane = setupPane(false);

    structure = "-Project\n" +
                " -PsiDirectory: src\n" +
                "  -PsiDirectory: sortByType\n" +
                "   a.properties\n" +
                "   X.txt\n" +
                "   -Resource Bundle 'xxx'\n" +
                "    xxx.properties\n" +
                "    xxx_en.properties\n" +
                "   xxx2.properties\n" +
                "   yyy.properties\n" +
                " External Libraries\n";

    PlatformTestUtil.assertTreeEqual(pane.getTree(), structure);
  }

  private AbstractProjectViewPSIPane setupPane(final boolean sortByType) {
    myStructure.setProviders(new ResourceBundleGrouper(getProject()));
    final AbstractProjectViewPSIPane pane = myStructure.createPane();
    pane.getTreeBuilder().setNodeDescriptorComparator(new GroupByTypeComparator(sortByType));
    // there should be xxx.properties in all test data
    PsiFile psiFile = getPsiManager().findFile(myFixture.findFileInTempDir(getTestName(true) + "/xxx.properties"));
    assert psiFile != null;
    pane.select(psiFile, psiFile.getVirtualFile(), sortByType);
    return pane;
  }
}
