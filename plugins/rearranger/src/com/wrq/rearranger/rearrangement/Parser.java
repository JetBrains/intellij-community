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
package com.wrq.rearranger.rearrangement;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.wrq.rearranger.entry.ClassContentsEntry;
import com.wrq.rearranger.entry.ClassEntry;
import com.wrq.rearranger.entry.PsiFileEntry;
import com.wrq.rearranger.entry.RangeEntry;
import com.wrq.rearranger.settings.RearrangerSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Creates a list of entries for classes and class members by parsing the Java file. */
public final class Parser {

  private static final Logger LOG = Logger.getInstance("#" + Parser.class.getName());

  private final Project            myProject;
  private final RearrangerSettings mySettings;
  private final PsiFile            myPsiFile;

  public Parser(final @NotNull Project project, final @NotNull RearrangerSettings settings, final @NotNull PsiFile psiFile) {
    myProject = project;
    mySettings = settings;
    myPsiFile = psiFile;
  }

  @NotNull
  public List<ClassContentsEntry> parseOuterLevel() {
    /**
     * Parse the top level contents of the PsiFile here.
     */
    PsiFileEntry fileEntry = new PsiFileEntry(mySettings);
    return fileEntry.parseFile(myProject, myPsiFile);
  }

  @SuppressWarnings("UnusedDeclaration")
  private static void dumpOuterClasses(final List<? extends ClassEntry> outerClasses) {
    LOG.debug("Outer class entries:");
    for (ClassEntry classEntry : outerClasses) {
      LOG.debug(classEntry.toString());
      if (classEntry.getContents() == null) {
        // this is a header or trailer, and isn't really a class.
        continue;
      }
      LOG.debug("   === class contents:");
      for (RangeEntry rangeEntry : classEntry.getContents()) {
        LOG.debug(rangeEntry.toString());
      }
      LOG.debug("   === end class contents:");
    }
  }
}
