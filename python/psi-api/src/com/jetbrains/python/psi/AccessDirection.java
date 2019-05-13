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
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;

/** How we refer to a name */
public enum AccessDirection {

  /** Reference */
  READ,

  /** Target of assignment */
  WRITE,

  /** Target of del statement */
  DELETE;

  /**
   * @param element
   * @return the access direction of element, judging from surrounding statements.
   */
  public static AccessDirection of(PyElement element) {
    final PsiElement parent = element.getParent();
    if (element instanceof PyTargetExpression) {
      return WRITE;
    }
    else if (parent instanceof PyAssignmentStatement) {
      for (PyExpression target : ((PyAssignmentStatement)parent).getTargets()) {
        if (target == element) {
          return WRITE;
        }
      }
    }
    else if (parent instanceof PyDelStatement) {
      return DELETE;
    }
    return READ;
  }
}
