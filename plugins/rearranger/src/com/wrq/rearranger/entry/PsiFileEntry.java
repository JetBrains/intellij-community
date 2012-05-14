/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.entry;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.wrq.rearranger.settings.RearrangerSettings;

import java.util.List;

/**
 * Dummy "outer class" which actually corresponds to the PsiFile.  Since classes contain
 * header text (text from the beginning of the class until the first recognized member), one or more
 * members, and trailing text, we can create a dummy "class" which will parse the file.  Header text
 * will include the package and import statements, and outer classes will be its members.
 */
public class PsiFileEntry
  extends ClassEntry
{
  public PsiFileEntry(RearrangerSettings settings) {
    super(null, null, 0, "", null, 0, settings);
  }

  /**
   * Strip off leading elements which do not belong to the first class, and place them in
   * a MiscellaneousText entry.
   *
   * @param project
   * @param psiFile
   * @param commentList
   * @return
   */
  public List<ClassContentsEntry> parseFile(Project project,
                                            PsiElement psiFile,
                                            List commentList)
  {
    int startingIndex = 0;
    for (int i = 0; i < psiFile.getChildren().length; i++) {
      PsiElement child = psiFile.getChildren()[i];
      if (child instanceof PsiClass) {
        do {
          startingIndex = i;
          i--;
          if (i >= 0) {
            child = psiFile.getChildren()[i];
          }
          else {
            break;
          }
        }
        while (child instanceof PsiWhiteSpace ||
               child instanceof PsiComment ||
               child.getText().length() == 0);
        break;
      }
    }
    MiscellaneousTextEntry miscellaneousTextEntry = null;
    if (startingIndex > 0) {
      miscellaneousTextEntry = new MiscellaneousTextEntry(
        psiFile.getChildren()[0],
        psiFile.getChildren()[startingIndex - 1],
        true, false
      );
    }
    parseRemainingClassContents(project, startingIndex, psiFile);
    if (miscellaneousTextEntry != null) {
      contents.add(0, miscellaneousTextEntry);
    }
    return contents;
  }
}
