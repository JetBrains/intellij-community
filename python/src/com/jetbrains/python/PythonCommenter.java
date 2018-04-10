/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.codeInsight.generation.IndentedCommenter;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.psi.PsiComment;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PythonCommenter implements CodeDocumentationAwareCommenter, IndentedCommenter {
  @Override
  public String getLineCommentPrefix() {
    return "# ";
  }

  @Override
  public String getBlockCommentPrefix() {
    return null;
  }

  @Override
  public String getBlockCommentSuffix() {
    return null;
  }

  @Override
  public String getCommentedBlockCommentPrefix() {
    return null;
  }

  @Override
  public String getCommentedBlockCommentSuffix() {
    return null;
  }

  @Override
  public IElementType getLineCommentTokenType() {
    return PyTokenTypes.END_OF_LINE_COMMENT;
  }

  @Override
  public IElementType getBlockCommentTokenType() {
    return null;
  }

  @Override
  public IElementType getDocumentationCommentTokenType() {
    return null;
  }

  @Override
  public String getDocumentationCommentPrefix() {
    return null;
  }

  @Override
  public String getDocumentationCommentLinePrefix() {
    return null;
  }

  @Override
  public String getDocumentationCommentSuffix() {
    return null;
  }

  @Override
  public boolean isDocumentationComment(PsiComment element) {
    return false;
  }

  @Nullable
  @Override
  public Boolean forceIndentedLineComment() {
    return true;
  }
}
