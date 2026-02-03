// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.xml;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiComment;
import org.jetbrains.annotations.NotNull;

public interface XmlComment extends XmlElement, PsiComment, XmlTagChild {

  @NotNull @NlsSafe String getCommentText();
}
