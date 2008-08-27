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
import com.intellij.refactoring.removemiddleman.DelegationUtils;
import com.intellij.refactoring.removemiddleman.RemoveMiddlemanProcessor;
import com.intellij.refactoring.util.classMembers.MemberInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RemoveMiddleManTest extends MultiFileTestCase{
  protected String getTestRoot() {
    return "/refactoring/removemiddleman/";
  }

  private void doTest() throws Exception {
    doTest(true);
  }

  private void doTest(final boolean delete) throws Exception {
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test");

        if (aClass == null) aClass = myJavaFacade.findClass("p.Test");
        assertNotNull("Class Test not found", aClass);

        final PsiField field = aClass.findFieldByName("myField", false);
        final Set<PsiMethod> methods = DelegationUtils.getDelegatingMethodsForField(field);
        List<MemberInfo> infos = new ArrayList<MemberInfo>();
        for (PsiMethod method : methods) {
          final MemberInfo info = new MemberInfo(method);
          info.setChecked(true);
          info.setToAbstract(delete);
          infos.add(info);
        }
        RemoveMiddlemanProcessor processor = new RemoveMiddlemanProcessor(field, infos.toArray(new MemberInfo[infos.size()]));
        processor.run();
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

  public void testNoGetter() throws Exception {
    doTest();
  }

  public void testSiblings() throws Exception {
    doTest();
  }

  public void testVisibility() throws Exception {
    doTest();
  }

  public void testInterface() throws Exception {
    doTest();
  }

  public void testPresentGetter() throws Exception {
    doTest();
  }

  public void testInterfaceDelegation() throws Exception {
    doTest();
  }
}