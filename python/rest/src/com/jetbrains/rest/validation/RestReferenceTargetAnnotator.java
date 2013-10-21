/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.rest.validation;

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.rest.RestBundle;
import com.jetbrains.rest.RestFile;
import com.jetbrains.rest.psi.RestReferenceTarget;

/**
 * Looks for double defined hyperlinks
 *
 * User : catherine
 */
public class RestReferenceTargetAnnotator extends RestAnnotator {

  @Override
  public void visitReferenceTarget(final RestReferenceTarget node) {
    RestFile file = (RestFile)node.getContainingFile();
    RestReferenceTarget[] targets = PsiTreeUtil.getChildrenOfType(file, RestReferenceTarget.class);
    String quotedName = node.getReferenceName();
    String name = node.getReferenceName(false);
    if (targets != null) {
      if ("__".equals(name) && !node.hasReference()) {
        getHolder().createWarningAnnotation(node, RestBundle.message("ANN.unusable.anonymous.target"));
      }
      for (RestReferenceTarget element : targets) {
        if ((element.getReferenceName().equalsIgnoreCase(name) || element.getReferenceName(false).equalsIgnoreCase(name) ||
            element.getReferenceName().equalsIgnoreCase(quotedName) || element.getReferenceName(false).equalsIgnoreCase(quotedName)) &&
                                    !element.equals(node) && ! "__".equals(name) && !"[#]".equals(quotedName) && !"[*]".equals(quotedName)) {
          getHolder().createWarningAnnotation(element, RestBundle.message("ANN.duplicate.target", name));
        }
      }
    }
  }
}
