/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.io.IOException;
import java.util.Locale;

import static com.intellij.util.containers.ContainerUtil.list;
import static com.intellij.util.containers.ContainerUtil.map;

/**
 * @author Dmitry Batkovich
 */
public class CustomResourceBundleTest extends LightPlatformCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ResourceBundleManager.getInstance(getProject()).loadState(new ResourceBundleManagerState());
  }

  public void testDissociateDefaultBaseName() {
    final PsiFile file = myFixture.addFileToProject("some_property_file.properties", "");
    final PsiFile file2 = myFixture.addFileToProject("some_property_filee.properties", "");
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
    assertNotNull(propertiesFile);
    final ResourceBundle resourceBundle = propertiesFile.getResourceBundle();
    final ResourceBundleManager resourceBundleBaseNameManager = ResourceBundleManager.getInstance(getProject());
    resourceBundleBaseNameManager.dissociateResourceBundle(resourceBundle);
    for (final PsiFile psiFile : list(file, file2)) {
      assertEquals(psiFile.getVirtualFile().getNameWithoutExtension(), resourceBundleBaseNameManager.getBaseName(psiFile));
      final PropertiesFile somePropertyFile = PropertiesImplUtil.getPropertiesFile(file);
      assertNotNull(somePropertyFile);
      assertOneElement(somePropertyFile.getResourceBundle().getPropertiesFiles());
    }
  }

  public void testCombineToCustomResourceBundleAndDissociateAfter() {
    final PropertiesFile file = PropertiesImplUtil.getPropertiesFile(myFixture.addFileToProject("resources-dev/my-app-dev.properties", ""));
    final PropertiesFile file2 = PropertiesImplUtil.getPropertiesFile(myFixture.addFileToProject("resources-prod/my-app-prod.properties", ""));
    assertNotNull(file);
    assertNotNull(file2);
    assertOneElement(file.getResourceBundle().getPropertiesFiles());
    assertOneElement(file2.getResourceBundle().getPropertiesFiles());

    final ResourceBundleManager resourceBundleBaseNameManager = ResourceBundleManager.getInstance(getProject());
    final String newBaseName = "my-app";
    resourceBundleBaseNameManager.combineToResourceBundle(list(file, file2), newBaseName);
    final ResourceBundle resourceBundle = file.getResourceBundle();
    assertEquals(2, resourceBundle.getPropertiesFiles().size());
    assertEquals(newBaseName, resourceBundle.getBaseName());

    resourceBundleBaseNameManager.dissociateResourceBundle(resourceBundle);
    assertOneElement(file.getResourceBundle().getPropertiesFiles());
    assertOneElement(file2.getResourceBundle().getPropertiesFiles());
  }

  public void testCustomResourceBundleFilesMovedOrDeleted() throws IOException {
    final PropertiesFile file = PropertiesImplUtil.getPropertiesFile(myFixture.addFileToProject("resources-dev/my-app-dev.properties", ""));
    final PropertiesFile file2 = PropertiesImplUtil.getPropertiesFile(myFixture.addFileToProject("resources-dev/my-app-test.properties", ""));
    final PropertiesFile file3 = PropertiesImplUtil.getPropertiesFile(myFixture.addFileToProject("resources-prod/my-app-prod.properties", ""));
    assertNotNull(file);
    assertNotNull(file2);
    assertNotNull(file3);
    assertOneElement(file.getResourceBundle().getPropertiesFiles());
    assertOneElement(file2.getResourceBundle().getPropertiesFiles());
    assertOneElement(file3.getResourceBundle().getPropertiesFiles());
    final ResourceBundleManager resourceBundleBaseNameManager = ResourceBundleManager.getInstance(getProject());
    resourceBundleBaseNameManager.combineToResourceBundle(list(file, file2, file3), "my-app");

    assertSize(3, file.getResourceBundle().getPropertiesFiles());

    final PsiDirectory newDir = PsiManager.getInstance(getProject()).findDirectory(myFixture.getTempDirFixture().findOrCreateDir("new-resources-dir"));
    new MoveFilesOrDirectoriesProcessor(getProject(), new PsiElement[] {file2.getContainingFile()}, newDir, false, false, null, null).run();
    file3.getContainingFile().delete();

    assertSize(2, file.getResourceBundle().getPropertiesFiles());

    final ResourceBundleManagerState state = ResourceBundleManager.getInstance(getProject()).getState();
    assertNotNull(state);
    assertSize(1, state.getCustomResourceBundles());
    assertSize(2, state.getCustomResourceBundles().get(0).getFileUrls());
  }

  public void testLocaleIfCanExtractFromCustomResourceBundle() {
    final PsiFile file = myFixture.addFileToProject("Base_Page.properties", "");
    final PsiFile file2 = myFixture.addFileToProject("Base_Page_en.properties", "");
    final ResourceBundleManager resourceBundleBaseNameManager = ResourceBundleManager.getInstance(getProject());
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
    assertNotNull(propertiesFile);
    resourceBundleBaseNameManager.dissociateResourceBundle(propertiesFile.getResourceBundle());
    resourceBundleBaseNameManager.combineToResourceBundle(map(list(file, file2), new Function<PsiFile, PropertiesFile>() {
      @Override
      public PropertiesFile fun(final PsiFile psiFile) {
        return PropertiesImplUtil.getPropertiesFile(psiFile);
      }
    }), "Base_Page");
    final PropertiesFile propertiesFile2 = PropertiesImplUtil.getPropertiesFile(file2);
    assertNotNull(propertiesFile2);
    final Locale locale = propertiesFile2.getLocale();
    assertEquals("en", locale.getLanguage());
  }

  public void testSuggestedCustomResourceBundleName() {
    final PsiFile file = myFixture.addFileToProject("Base_Page.properties", "");
    final PsiFile file2 = myFixture.addFileToProject("Base_Page_en.properties", "");
    final String baseName =
      PropertiesUtil.getDefaultBaseName(ContainerUtil.map(list(file, file2), new Function<PsiFile, PropertiesFile>() {
        @Override
        public PropertiesFile fun(PsiFile psiFile) {
          return PropertiesImplUtil.getPropertiesFile(file);
        }
      }));
    assertEquals("Base_Page", baseName);
  }
}
