/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.properties;

import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.ide.projectView.impl.GroupByTypeComparator;
import com.intellij.lang.properties.projectView.ResourceBundleGrouper;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.projectView.TestProjectTreeStructure;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

import java.util.ArrayList;
import java.util.List;

public class PropertiesProjectViewTest extends LightPlatformCodeInsightFixtureTestCase {
  private TestProjectTreeStructure myStructure;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myStructure = new TestProjectTreeStructure(getProject(), myFixture.getTestRootDisposable());
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

  public void testCustomBundle() {
    myFixture.copyDirectoryToProject(getTestName(true), getTestName(true));
    List<PropertiesFile> customBundleFiles = new ArrayList<>(2);
    PropertiesReferenceManager.getInstance(getProject()).processAllPropertiesFiles((baseName, propertiesFile) -> {
      customBundleFiles.add(propertiesFile);
      return true;
    });
    ResourceBundleManager.getInstance(getProject()).combineToResourceBundle(customBundleFiles, "some");

    final AbstractProjectViewPSIPane pane = setupPane(true);
    String structure = "-Project\n" +
                       " -PsiDirectory: src\n" +
                       "  -PsiDirectory: customBundle\n" +
                       "   -PsiDirectory: dev\n" +
                       "    some.dev.properties (custom RB: some)\n" +
                       "   -PsiDirectory: prod\n" +
                       "    some.prod.properties (custom RB: some)\n" +
                       " External Libraries\n";

    PlatformTestUtil.assertTreeEqual(pane.getTree(), structure);
  }

  public void testFewBundles() {
    myFixture.copyDirectoryToProject(getTestName(true), getTestName(true));
    List<PropertiesFile> customBundleFiles = new ArrayList<>(2);
    PropertiesReferenceManager.getInstance(getProject()).processAllPropertiesFiles((baseName, propertiesFile) -> {
      if (baseName.contains("custom")) {
        customBundleFiles.add(propertiesFile);
      }
      return true;
    });
    ResourceBundleManager.getInstance(getProject()).combineToResourceBundle(customBundleFiles, "custom");

    final AbstractProjectViewPSIPane pane = setupPane(true);
    String structure = "-Project\n" +
                       " -PsiDirectory: src\n" +
                       "  -PsiDirectory: fewBundles\n" +
                       "   -PsiDirectory: dev\n" +
                       "    custom.prod.properties (custom RB: custom)\n" +
                       "   custom.dev.properties (custom RB: custom)\n" +
                       "   xxx.properties\n" +
                       "   -Resource Bundle 'a'\n" +
                       "    a.properties\n" +
                       "    a_en.properties\n" +
                       "   -Resource Bundle 'b'\n" +
                       "    b.properties\n" +
                       "    b_fr.properties\n" +
                       " External Libraries\n";

    PlatformTestUtil.assertTreeEqual(pane.getTree(), structure);
  }

  private AbstractProjectViewPSIPane setupPane(final boolean sortByType) {
    myStructure.setProviders(new ResourceBundleGrouper(getProject()));
    final AbstractProjectViewPSIPane pane = myStructure.createPane();
    pane.installComparator(new GroupByTypeComparator(sortByType));
    PropertiesReferenceManager.getInstance(getProject()).processAllPropertiesFiles((baseName, propertiesFile) -> {
      pane.select(propertiesFile, propertiesFile.getVirtualFile(), sortByType);
      return true;
    });

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    return pane;
  }
}
