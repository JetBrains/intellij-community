/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.psi;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public interface PyTypeCommentOwner extends PsiElement {
  /**
   * Returns a special comment that follows element definition and starts with conventional "type:" prefix. 
   * It is supposed to contain type annotation in PEP 484 compatible format. For further details see sections
   * <a href="https://www.python.org/dev/peps/pep-0484/#type-comments">Type Comments</a> and 
   * <a href="https://www.python.org/dev/peps/pep-0484/#suggested-syntax-for-python-2-7-and-straddling-code">Suggested syntax for Python 2.7 and straddling code</a> and
   * in PEP 484.
   * <p/>
   * Use {@link #getTypeCommentAnnotation()} to get its content with the prefix stripped accessing either stubs or AST.
   *
   * @see #getTypeCommentAnnotation()
   */
  @Nullable
  PsiComment getTypeComment();

  /**
   * Returns type annotation after the "type:" prefix extracted from the commentary returned by {@link #getTypeComment()}.
   *
   * @see #getTypeComment()
   */
  @Nullable
  String getTypeCommentAnnotation();
}
