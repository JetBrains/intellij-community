/*
 * User: anna
 * Date: 20-Aug-2008
 */
package com.intellij.refactoring;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.extractclass.ExtractClassProcessor;

import java.util.ArrayList;

public class ExtractClassTest extends MultiFileTestCase{
  protected String getTestRoot() {
    return "/refactoring/extractClass/";
  }

  private void doTestMethod() throws Exception {
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test");

        assertNotNull("Class Test not found", aClass);

        final ArrayList<PsiMethod> methods = new ArrayList<PsiMethod>();
        methods.add(aClass.findMethodsByName("foo", false)[0]);
        ExtractClassProcessor processor = new ExtractClassProcessor(aClass, new ArrayList<PsiField>(), methods, new ArrayList<PsiClass>(), "", "Extracted");
        processor.run();
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

  public void testStatic() throws Exception {
    doTestMethod();
  }

  public void testFieldReference() throws Exception {
    doTestMethod();
  }

  public void testVarargs() throws Exception {
    doTestMethod();
  }

  public void testNoDelegation() throws Exception {
    doTestMethod();
  }

  public void testNoFieldDelegation() throws Exception {
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test");

        assertNotNull("Class Test not found", aClass);

        final ArrayList<PsiMethod> methods = new ArrayList<PsiMethod>();
        methods.add(aClass.findMethodsByName("bar", false)[0]);

        final ArrayList<PsiField> fields = new ArrayList<PsiField>();
        fields.add(aClass.findFieldByName("myT", false));

        ExtractClassProcessor processor = new ExtractClassProcessor(aClass, fields, methods, new ArrayList<PsiClass>(), "", "Extracted");
        processor.run();
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

  private void doTestInnerClass() throws Exception {
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test");

        assertNotNull("Class Test not found", aClass);

        final ArrayList<PsiClass> classes = new ArrayList<PsiClass>();
        classes.add(aClass.findInnerClassByName("Inner", false));
        ExtractClassProcessor processor = new ExtractClassProcessor(aClass, new ArrayList<PsiField>(), new ArrayList<PsiMethod>(), classes, "", "Extracted");
        processor.run();
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

  public void testInner() throws Exception {
    doTestInnerClass();
  }
}