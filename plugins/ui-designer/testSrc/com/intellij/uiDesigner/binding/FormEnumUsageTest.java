/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 11.10.2006
 * Time: 16:03:23
 */
package com.intellij.uiDesigner.binding;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.IncorrectOperationException;

public class FormEnumUsageTest extends PsiTestCase {
  private VirtualFile myTestProjectRoot;

  protected void setUp() throws Exception {
    super.setUp();

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          try{
            String root = PluginPathManager.getPluginHomePath("ui-designer") + "/testData/binding/" + getTestName(true);
            PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17());
            myTestProjectRoot = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
          }
          catch(Exception e){
            LOG.error(e);
          }
        }
      }
    );
  }

  @Override protected void tearDown() throws Exception {
    myTestProjectRoot = null;
    super.tearDown();
  }

  public void testEnumUsage() throws IncorrectOperationException {
    LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        try {
          createFile(myModule, myTestProjectRoot, "PropEnum.java", "public enum PropEnum { valueA, valueB }");
          createFile(myModule, myTestProjectRoot, "CustomComponent.java",
                     "public class CustomComponent extends JLabel { private PropEnum e; public PropEnum getE() { return e; } public void setE(E newE) { e = newE; } }");
        }
        catch (Exception e) {
          fail(e.getMessage());
        }
      }
    }, "", null);

    PsiClass enumClass = myJavaFacade.findClass("PropEnum", ProjectScope.getAllScope(myProject));
    PsiField valueBField = enumClass.findFieldByName("valueB", false);
    assertNotNull(valueBField);
    assertTrue(valueBField instanceof PsiEnumConstant);
    final PsiClass componentClass = myJavaFacade.findClass("CustomComponent", ProjectScope.getAllScope(myProject));
    assertNotNull(componentClass);

    assertEquals(1, ReferencesSearch.search(componentClass).findAll().size());

    assertEquals(1, ReferencesSearch.search(valueBField).findAll().size());
  }

}
