/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.dependencies;

import com.intellij.testFramework.TestSourceBasedTestCase;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.analysis.AnalysisScope;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.ui.DependenciesPanel;
import com.intellij.idea.IdeaTestUtil;

import javax.swing.*;
import java.util.Calendar;

public class DependenciesPanelTest extends TestSourceBasedTestCase{
  public void testDependencies(){
    if (IdeaTestUtil.bombExplodes(2005, Calendar.JANUARY, 14, 13, 0, "max", "AnilizeDependencies should show file dependencies " +
                                                                            "when the file is selected initially")) {
      final PsiClass[] classes = getPackageDirectory("com/package1").getPackage().getClasses();
      final PsiFile file = classes[0].getContainingFile();
      final AnalysisScope scope = new AnalysisScope(file, new AnalysisScope.PsiFileFilter() {
                                                      public boolean accept(PsiFile another) {
                                                        return true;
                                                      }
                                                    });
      final DependenciesBuilder builder = new DependenciesBuilder(myProject, scope);
      builder.analyze();
      final DependenciesPanel dependenciesPanel =
       new DependenciesPanel(myProject, builder);
      JTree leftTree = dependenciesPanel.getLeftTree();
      IdeaTestUtil.assertTreeEqual(leftTree, "-Root\n" +
                            " Library Classes\n" +
                            " -Production Classes\n" +
                            "  -" + myModule.getName() + "\n" +
                            "   -com.package1\n" +
                            "    [Class1.java]\n" +
                                " Test Classes\n", true);

      JTree rightTree = dependenciesPanel.getRightTree();
      IdeaTestUtil.assertTreeEqual(rightTree, "-Root\n" +
                             " Library Classes\n" +
                             " -Production Classes\n" +
                             "  -" + myModule.getName() + "\n" +
                             "   -com.package1\n" +
                             " Test Classes\n", true);
    }

  }

  protected String getTestPath() {
    return "dependencies";
  }
}
