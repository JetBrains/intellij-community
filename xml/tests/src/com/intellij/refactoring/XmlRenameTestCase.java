// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.codeInsight.JavaCodeInsightTestCase;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;

import java.io.File;

/**
 * @author spleaner
 */
public abstract class XmlRenameTestCase extends JavaCodeInsightTestCase {
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/xmlRename/";
  }


  protected void doTest(final String newName, String ext) throws Exception {
    final String name = getTestName(false);
    doTest(newName,new String[] {name+ "."+ ext},name + "_after."+ ext);

    // if the dtd gets renamed then subsequent same renaming fails!
    doTest(newName,new String[] {name+ "."+ ext},name + "_after."+ ext);
  }

  protected void doTest(final String newName, String[] fileNames,String fileNameAfter) throws Exception {
    doTest(newName, fileNames, fileNameAfter, null);
  }

  protected void doTest(final String newName, String[] fileNames,String fileNameAfter, Runnable afterConfigure) throws Exception {
    VirtualFile[] files = new VirtualFile[fileNames.length];

    for (int i = 0; i < fileNames.length; i++) {
      files[i] = findVirtualFile( fileNames[i]);
    }
    configureByFiles(null, files);
    if (afterConfigure != null) afterConfigure.run();
    performAction(newName);
    checkResultByFile(  fileNameAfter);
  }

  protected void checkDescriptorRenamed() {
    final PsiElement elementAt = myFile.findElementAt(myEditor.getCaretModel().getOffset());
    final XmlTag parentOfType = PsiTreeUtil.getParentOfType(elementAt, XmlTag.class);
    final XmlElementDescriptor descriptor = parentOfType.getDescriptor();
    assertTrue("Rename of another func dcl failed",descriptor instanceof XmlElementDescriptorImpl && parentOfType.getLocalName().equals(descriptor.getName()));
  }

  protected PsiReference[] findReferencesToReferencedElementAtCaret() {
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil
                                                                         .ELEMENT_NAME_ACCEPTED |
                                                                       TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);

    final PsiReference[] references =
      ReferencesSearch.search(element, GlobalSearchScope.allScope(myProject), true).toArray(PsiReference.EMPTY_ARRAY);
    return references;
  }

  protected void performAction(String newName) {
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil
                                                                         .ELEMENT_NAME_ACCEPTED |
                                                                       TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertTrue(newName + " is not a valid name", RenameUtil.isValidName(myProject, element, newName));
    new RenameProcessor(myProject, element, newName, false, false).run();
  }
}
