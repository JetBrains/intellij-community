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

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.wrq.rearranger.ruleinstance.RuleInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Emits a new document from the rearranged entries. */
public final class Emitter {

  @NotNull private final StringBuilder      myStringBuffer        = new StringBuilder();
  @NotNull private final List<RuleInstance> myResultRuleInstances = new ArrayList<RuleInstance>();

  @NotNull private final PsiFile  myFile;
  @NotNull private final Document myDocument;

  public Emitter(@NotNull final PsiFile file, @NotNull final List<RuleInstance> resultRuleInstances, @NotNull final Document document) {
    myFile = file;
    myResultRuleInstances.addAll(resultRuleInstances);
    myDocument = document;
    myStringBuffer.ensureCapacity(myFile.getText().length() + 100 /* room for inserted blank lines */);
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  public StringBuilder getTextBuffer() {
    return myStringBuffer;
  }

  public void emitRearrangedDocument() {
    emitRuleInstances(myResultRuleInstances);
    myDocument.replaceString(
      myFile.getTextRange().getStartOffset(),
      myFile.getTextRange().getEndOffset(),
      myStringBuffer.toString()
    );
  }

  public void emitRuleInstances(@Nullable List<RuleInstance> resultRuleInstances) {
    if (resultRuleInstances == null) {
      return;
    }
    for (RuleInstance ruleInstance : resultRuleInstances) {
      ruleInstance.emit(this);
    }
  }
}
