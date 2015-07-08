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
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.io.IOException;
import java.util.ArrayList;
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

  public void testPropertiesFilesDefaultCombiningToResourceBundle() {
    final PsiFile file = myFixture.addFileToProject("prop_core_en.properties", "");
    final PsiFile file2 = myFixture.addFileToProject("prop_core_fi.properties", "");
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
    final PropertiesFile propertiesFile2 = PropertiesImplUtil.getPropertiesFile(file2);
    assertNotNull(propertiesFile);
    assertNotNull(propertiesFile2);
    final ResourceBundle bundle = propertiesFile.getResourceBundle();
    final ResourceBundle bundle2 = propertiesFile2.getResourceBundle();
    assertTrue(bundle.equals(bundle2));
    assertSize(2, bundle.getPropertiesFiles());
    assertTrue(bundle.getDefaultPropertiesFile().equals(bundle2.getDefaultPropertiesFile()));
    assertEquals("prop_core", bundle.getBaseName());

    assertNotSame(propertiesFile.getLocale().getLanguage(), propertiesFile.getLocale().getDisplayLanguage());
    assertNotSame(propertiesFile2.getLocale().getLanguage(), propertiesFile2.getLocale().getDisplayLanguage());
  }

  public void testPropertiesFileNotAssociatedWhileLanguageCodeNotRecognized() {
    final PsiFile file = myFixture.addFileToProject("some_property_file.properties", "");
    final PsiFile file2 = myFixture.addFileToProject("some_property_fil.properties", "");
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
    assertNotNull(propertiesFile);
    final ResourceBundle resourceBundle = propertiesFile.getResourceBundle();
    assertSize(1, resourceBundle.getPropertiesFiles());

    final PropertiesFile propertiesFile2 = PropertiesImplUtil.getPropertiesFile(file2);
    assertNotNull(propertiesFile2);
    final ResourceBundle resourceBundle2 = propertiesFile.getResourceBundle();
    assertSize(1, resourceBundle2.getPropertiesFiles());

    assertEquals(PropertiesUtil.DEFAULT_LOCALE, propertiesFile.getLocale());
  }

  public void testLanguageCodeNotRecognized() {
    final PsiFile file = myFixture.addFileToProject("p.properties", "");
    final PsiFile file2 = myFixture.addFileToProject("p_asd.properties", "");

    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
    final PropertiesFile propertiesFile2 = PropertiesImplUtil.getPropertiesFile(file2);
    assertNotNull(propertiesFile);
    assertNotNull(propertiesFile2);
    final ResourceBundle bundle = propertiesFile.getResourceBundle();
    final ResourceBundle bundle2 = propertiesFile2.getResourceBundle();
    assertSize(1, bundle.getPropertiesFiles());
    assertSize(1, bundle2.getPropertiesFiles());
    assertEquals("p", bundle.getBaseName());
    assertEquals("p_asd", bundle2.getBaseName());

    final ResourceBundleManager manager = ResourceBundleManager.getInstance(getProject());
    final ArrayList<PropertiesFile> rawBundle = ContainerUtil.newArrayList(propertiesFile, propertiesFile2);
    final String suggestedBaseName = PropertiesUtil.getDefaultBaseName(rawBundle);
    assertEquals("p", suggestedBaseName);
    manager.combineToResourceBundle(rawBundle, suggestedBaseName);

    assertEquals("asd", propertiesFile2.getLocale().getLanguage());
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
    final PropertiesFile file2 = PropertiesImplUtil.getPropertiesFile(
      myFixture.addFileToProject("resources-dev/my-app-test.properties", ""));
    final PropertiesFile file3 = PropertiesImplUtil.getPropertiesFile(
      myFixture.addFileToProject("resources-prod/my-app-prod.properties", ""));
    assertNotNull(file);
    assertNotNull(file2);
    assertNotNull(file3);
    assertOneElement(file.getResourceBundle().getPropertiesFiles());
    assertOneElement(file2.getResourceBundle().getPropertiesFiles());
    assertOneElement(file3.getResourceBundle().getPropertiesFiles());
    final ResourceBundleManager resourceBundleBaseNameManager = ResourceBundleManager.getInstance(getProject());
    resourceBundleBaseNameManager.combineToResourceBundle(list(file, file2, file3), "my-app");

    assertSize(3, file.getResourceBundle().getPropertiesFiles());

    final PsiDirectory newDir = PsiManager.getInstance(getProject()).findDirectory(
      myFixture.getTempDirFixture().findOrCreateDir("new-resources-dir"));
    new MoveFilesOrDirectoriesProcessor(getProject(), new PsiElement[] {file2.getContainingFile()}, newDir, false, false, null, null).run();
    file3.getContainingFile().delete();

    assertSize(2, file.getResourceBundle().getPropertiesFiles());

    final ResourceBundleManagerState state = ResourceBundleManager.getInstance(getProject()).getState();
    assertNotNull(state);
    assertSize(1, state.getCustomResourceBundles());
    assertSize(2, state.getCustomResourceBundles().get(0).getFileUrls());
  }

  public void testSuggestedCustomResourceBundleName() {
    final PsiFile file = myFixture.addFileToProject("Base_Page.properties", "");
    final PsiFile file2 = myFixture.addFileToProject("Base_Page_en.properties", "");
    final String baseName =
      PropertiesUtil.getDefaultBaseName(map(list(file, file2), new Function<PsiFile, PropertiesFile>() {
        @Override
        public PropertiesFile fun(PsiFile psiFile) {
          return PropertiesImplUtil.getPropertiesFile(file);
        }
      }));
    assertEquals("Base_Page", baseName);
  }

  public void testResourceBundleManagerUpdatesProperlyWhileDirRemoval() {
    myFixture.addFileToProject("qwe/asd/p.properties", "");
    final PsiFile file = myFixture.addFileToProject("qwe/asd/p_en.properties", "");
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
    assertNotNull(propertiesFile);
    final ResourceBundleManager resourceBundleManager = ResourceBundleManager.getInstance(getProject());
    resourceBundleManager.dissociateResourceBundle(propertiesFile.getResourceBundle());

    final PropertiesFile propFile1 = PropertiesImplUtil.getPropertiesFile(myFixture.addFileToProject("qwe1/asd1/p.properties", ""));
    final PropertiesFile propFile2 = PropertiesImplUtil.getPropertiesFile(myFixture.addFileToProject("qwe1/asd1/p_abc.properties", ""));
    assertNotNull(propFile1);
    assertNotNull(propFile2);
    resourceBundleManager.combineToResourceBundle(ContainerUtil.newArrayList(propFile1, propFile2), "p");

    final PsiFile someFile = myFixture.addFileToProject("to_remove/asd.txt", "");
    final PsiDirectory toRemove = someFile.getParent();
    assertNotNull(toRemove);
    toRemove.delete();
    final ResourceBundleManagerState state = resourceBundleManager.getState();
    assertNotNull(state);
    assertSize(1, state.getCustomResourceBundles());
    assertSize(2, state.getDissociatedFiles());

    final PsiDirectory directory = propertiesFile.getParent().getParent();
    assertNotNull(directory);
    directory.delete();
    assertSize(1, state.getCustomResourceBundles());
    assertSize(0, state.getDissociatedFiles());

    final PsiDirectory directory1 = propFile1.getParent().getParent();
    assertNotNull(directory1);
    directory1.delete();
    assertSize(0, state.getCustomResourceBundles());
    assertSize(0, state.getDissociatedFiles());
  }

  public void testResourceBundleManagerUpdatesProperlyWhileDirMove() {
    final PropertiesFile propFile1 = PropertiesImplUtil.getPropertiesFile(myFixture.addFileToProject("qwe/p.properties", ""));
    final PropertiesFile propFile2 = PropertiesImplUtil.getPropertiesFile(myFixture.addFileToProject("qwe/p_abc.properties", ""));
    assertNotNull(propFile1);
    assertNotNull(propFile2);
    myFixture.addFileToProject("qwe/q.properties", "");
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(myFixture.addFileToProject("qwe/q_fr.properties", ""));
    assertNotNull(propertiesFile);
    assertSize(2, propertiesFile.getResourceBundle().getPropertiesFiles());

    final ResourceBundleManager resourceBundleManager = ResourceBundleManager.getInstance(getProject());
    resourceBundleManager.combineToResourceBundle(ContainerUtil.newArrayList(propFile1, propFile2), "p");
    resourceBundleManager.dissociateResourceBundle(propertiesFile.getResourceBundle());
    assertSize(1, propertiesFile.getResourceBundle().getPropertiesFiles());
    assertSize(2, propFile2.getResourceBundle().getPropertiesFiles());

    final PsiDirectory toMove = myFixture.addFileToProject("asd/temp.txt", "").getParent();
    assertNotNull(toMove);
    MoveFilesOrDirectoriesUtil.doMoveDirectory(propFile1.getParent(), toMove);
    final PsiDirectory movedDir = toMove.findSubdirectory("qwe");
    assertNotNull(movedDir);

    final PropertiesFile newPropFile1 = PropertiesImplUtil.getPropertiesFile(movedDir.findFile("p.properties"));
    assertNotNull(newPropFile1);
    assertSize(2, newPropFile1.getResourceBundle().getPropertiesFiles());

    final PropertiesFile newPropertiesFile = PropertiesImplUtil.getPropertiesFile(movedDir.findFile("q_fr.properties"));
    assertNotNull(newPropertiesFile);
    assertSize(1, newPropertiesFile.getResourceBundle().getPropertiesFiles());
  }
}
