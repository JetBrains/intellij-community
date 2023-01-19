/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.properties;

import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.GroupByTypeComparator;
import com.intellij.lang.properties.projectView.ResourceBundleGrouper;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.projectView.TestProjectTreeStructure;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

public class PropertiesProjectViewTest extends BasePlatformTestCase {
  private TestProjectTreeStructure myStructure;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myStructure = new TestProjectTreeStructure(getProject(), myFixture.getTestRootDisposable());
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/tests/testData/propertiesFile/projectView";
  }

  public void testBundle() {
    myFixture.copyDirectoryToProject(getTestName(true), getTestName(true));
    final AbstractProjectViewPane pane = setupPane(true);

    String structure = """
      -Project
       -PsiDirectory: src
        -PsiDirectory: bundle
         yyy.properties
         -Resource Bundle 'xxx'
          xxx.properties
          xxx_en.properties
          xxx_ru_RU.properties
         X.txt
       External Libraries
      """;
    PlatformTestUtil.assertTreeEqual(pane.getTree(), structure);
  }

  public void testStandAlone() {
    myFixture.copyDirectoryToProject(getTestName(true), getTestName(true));
    final AbstractProjectViewPane pane = setupPane(true);

    String structure = """
      -Project
       -PsiDirectory: src
        -PsiDirectory: standAlone
         a.properties
         xxx.properties
         xxx2.properties
         yyy.properties
         X.txt
       External Libraries
      """;

    PlatformTestUtil.assertTreeEqual(pane.getTree(), structure);
  }

  public void testSortByType() {
    myFixture.copyDirectoryToProject(getTestName(true), getTestName(true));
    AbstractProjectViewPane pane = setupPane(true);

    String structure = """
      -Project
       -PsiDirectory: src
        -PsiDirectory: sortByType
         a.properties
         xxx2.properties
         yyy.properties
         -Resource Bundle 'xxx'
          xxx.properties
          xxx_en.properties
         X.txt
       External Libraries
      """;

    PlatformTestUtil.assertTreeEqual(pane.getTree(), structure);

    pane = setupPane(false);

    structure = """
      -Project
       -PsiDirectory: src
        -PsiDirectory: sortByType
         a.properties
         X.txt
         -Resource Bundle 'xxx'
          xxx.properties
          xxx_en.properties
         xxx2.properties
         yyy.properties
       External Libraries
      """;

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

    final AbstractProjectViewPane pane = setupPane(true);
    String structure = """
      -Project
       -PsiDirectory: src
        -PsiDirectory: customBundle
         -PsiDirectory: dev
          some.dev.properties (custom RB: some)
         -PsiDirectory: prod
          some.prod.properties (custom RB: some)
       External Libraries
      """;

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

    final AbstractProjectViewPane pane = setupPane(true);
    String structure = """
      -Project
       -PsiDirectory: src
        -PsiDirectory: fewBundles
         -PsiDirectory: dev
          custom.prod.properties (custom RB: custom)
         custom.dev.properties (custom RB: custom)
         xxx.properties
         -Resource Bundle 'a'
          a.properties
          a_en.properties
         -Resource Bundle 'b'
          b.properties
          b_fr.properties
       External Libraries
      """;

    PlatformTestUtil.assertTreeEqual(pane.getTree(), structure);
  }

  private AbstractProjectViewPane setupPane(final boolean sortByType) {
    myStructure.setProviders(new ResourceBundleGrouper(getProject()));
    final AbstractProjectViewPane pane = myStructure.createPane();
    pane.installComparator(new GroupByTypeComparator(sortByType));
    PropertiesReferenceManager.getInstance(getProject()).processAllPropertiesFiles((baseName, propertiesFile) -> {
      pane.select(propertiesFile, propertiesFile.getVirtualFile(), sortByType);
      return true;
    });

    PlatformTestUtil.waitWhileBusy(pane.getTree());
    PlatformTestUtil.expandAll(pane.getTree());
    return pane;
  }
}
