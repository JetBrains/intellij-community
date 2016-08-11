/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
 * User: anna
 * Date: 18-Jun-2007
 */
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.*;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiNonJavaFileReferenceProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.theoryinpractice.testng.configuration.browser.SuiteBrowser;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UndeclaredTestInspection extends BaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + UndeclaredTestInspection.class.getName());

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return TestNGUtil.TESTNG_GROUP_NAME;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Undeclared test";
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "UndeclaredTests";
  }

  @Nullable
  public ProblemDescriptor[] checkClass(@NotNull final PsiClass aClass,
                                        @NotNull final InspectionManager manager,
                                        final boolean isOnTheFly) {
    if (TestNGUtil.hasTest(aClass) && PsiClassUtil.isRunnableClass(aClass, true)) {
      final Project project = aClass.getProject();
      final String qName = aClass.getQualifiedName();
      if (qName == null) return null;
      final String packageQName = StringUtil.getPackageName(qName);
      final List<String> names = new ArrayList<>();
      for(int i = 0; i < qName.length(); i++) {
        if (qName.charAt(i) == '.') {
          names.add(qName.substring(0, i));
        }
      }
      names.add(qName);
      Collections.reverse(names);

      for (final String name : names) {
        final boolean isFullName = qName.equals(name);
        final boolean[] found = new boolean[]{false};
        PsiSearchHelper.SERVICE.getInstance(project)
          .processUsagesInNonJavaFiles(name, new PsiNonJavaFileReferenceProcessor() {
            public boolean process(final PsiFile file, final int startOffset, final int endOffset) {
              if (file.findReferenceAt(startOffset) != null) {
                if (!isFullName) { //special package tag required
                  final XmlTag tag = PsiTreeUtil.getParentOfType(file.findElementAt(startOffset), XmlTag.class);
                  if (tag == null || !tag.getName().equals("package")) {
                    return true;
                  }
                  final XmlAttribute attribute = tag.getAttribute("name");
                  if (attribute == null) return true;
                  final String value = attribute.getValue();
                  if (value == null) return true;
                  if (!value.endsWith(".*") && !value.equals(packageQName)) return true;
                }
                found[0] = true;
                return false;
              }
              return true;
            }
          }, new TestNGSearchScope(project));
        if (found[0]) return null;
      }
      final PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
      LOG.assertTrue(nameIdentifier != null);
      return new ProblemDescriptor[]{manager.createProblemDescriptor(nameIdentifier, "Undeclared test \'" + aClass.getName() + "\'",
                                                                     isOnTheFly, new LocalQuickFix[]{new RegisterClassFix(aClass),
                                                                       new CreateTestngFix()},
                                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};
    }
    return null;
  }

  private static class RegisterClassFix implements LocalQuickFix {
    private final PsiClass myClass;

    public RegisterClassFix(final PsiClass aClass) {
      myClass = aClass;
    }

    @NotNull
    public String getName() {
      return "Register \'" + myClass.getName() + "\'";
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiClass psiClass = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class);
      LOG.assertTrue(psiClass != null);
      SwingUtilities.invokeLater(() -> {
        final String testngXmlPath = new SuiteBrowser(project).showDialog();
        if (testngXmlPath == null) return;
        final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(testngXmlPath);
        LOG.assertTrue(virtualFile != null);
        final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
        LOG.assertTrue(psiFile instanceof XmlFile);
        final XmlFile testngXML = (XmlFile)psiFile;
        new WriteCommandAction(project, getName(), testngXML) {
          protected void run(@NotNull final Result result) throws Throwable {
            patchTestngXml(testngXML, psiClass);
          }
        }.execute();
      });
    }
  }

  //make public for tests only
  public static void patchTestngXml(final XmlFile testngXML, final PsiClass psiClass) {
    final XmlTag rootTag = testngXML.getDocument().getRootTag();
    if (rootTag != null && rootTag.getName().equals("suite")) {
      try {
        XmlTag testTag = rootTag.findFirstSubTag("test");
        if (testTag == null) {
          testTag = (XmlTag)rootTag.add(rootTag.createChildTag("test", rootTag.getNamespace(), null, false));
          testTag.setAttribute("name", psiClass.getName());
        }
        XmlTag classesTag = testTag.findFirstSubTag("classes");
        if (classesTag == null) {
          classesTag = (XmlTag)testTag.add(testTag.createChildTag("classes", testTag.getNamespace(), null, false));
        }
        final XmlTag classTag = (XmlTag)classesTag.add(classesTag.createChildTag("class", classesTag.getNamespace(), null, false));
        final String qualifiedName = psiClass.getQualifiedName();
        LOG.assertTrue(qualifiedName != null);
        classTag.setAttribute("name", qualifiedName);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private static class CreateTestngFix implements LocalQuickFix {
    @NotNull
    public String getName() {
      return "Create suite";
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      final PsiClass psiClass = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class);
      SwingUtilities.invokeLater(() -> {
        final VirtualFile file = FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, null);
        if (file != null) {
          final PsiManager psiManager = PsiManager.getInstance(project);
          final PsiDirectory directory = psiManager.findDirectory(file);
          LOG.assertTrue(directory != null);
          new WriteCommandAction(project, getName(), null) {
            protected void run(@NotNull final Result result) throws Throwable {
              XmlFile testngXml = (XmlFile)PsiFileFactory.getInstance(psiManager.getProject())
                .createFileFromText("testng.xml", "<!DOCTYPE suite SYSTEM \"http://testng.org/testng-1.0.dtd\">\n<suite></suite>");
              try {
                testngXml = (XmlFile)directory.add(testngXml);
              }
              catch (IncorrectOperationException e) {
                //todo suggest new name
                return;
              }
              patchTestngXml(testngXml, psiClass);
            }
          }.execute();
        }
      });
    }
  }
}
