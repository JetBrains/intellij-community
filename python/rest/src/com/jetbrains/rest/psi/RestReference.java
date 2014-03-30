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
package com.jetbrains.rest.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.rest.validation.RestElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class RestReference extends RestElement {
  public RestReference(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "RestReference:" + getNode().getElementType().toString();
  }

  public String getReferenceText() {
    String text = getNode().getText();
    text = StringUtil.replace(text, "\n", " ");
    text = text.replaceAll("\\\\([^\\\\]+)", "$1");
    if (text.startsWith("`") && text.endsWith("`_"))
      return text.substring(1, text.length()-2);
    if (text.endsWith("__"))
      return "__";
    if (text.startsWith("|") && text.endsWith("|"))
      return text;
    return text.substring(0, text.length()-1);
  }

  @Override
  protected void acceptRestVisitor(RestElementVisitor visitor) {
    visitor.visitReference(this);
  }

  public RestElement resolve() {
    String name = getReferenceText();
    PsiFile file = getContainingFile();
    RestReferenceTarget[] elements = PsiTreeUtil.getChildrenOfType(file, RestReferenceTarget.class);
    if (elements != null) {
      if (name.equals("__") || name.equals("[*]") || name.equals("[#]"))
        return findAnonimousTarget(file, elements);

      for (RestReferenceTarget element : elements) {
        if (element.getReferenceName().equalsIgnoreCase(name) || element.getReferenceName(false).equalsIgnoreCase(name)) {
          return element;
        }
      }
    }
    //TODO[catherine]: targets are not better than titles for resolving
    // they should have the same ancestor
    RestTitle[] titles = PsiTreeUtil.getChildrenOfType(file, RestTitle.class);
    if (titles != null) {
      for (RestTitle element : titles) {
        if (name.equalsIgnoreCase(element.getName())) {
          return element;
        }
      }
    }
    return null;
  }

  private RestReferenceTarget findAnonimousTarget(PsiFile file, RestReferenceTarget[] targets) {
    String name = getReferenceText();
    RestReference[] references = PsiTreeUtil.getChildrenOfType(file, RestReference.class);
    int refIndex = 1;
    int i = 0;
    while (!references[i].equals(this)) {
      if (references[i].getReferenceText().equals(name))
        ++refIndex;
      ++i;
    }

    int targetIndex = 0;
    for (int j = 0; j != targets.length; ++j) {
      if (targets[j].getReferenceName().equals(name))
        ++targetIndex;
      if (targetIndex == refIndex)
        return targets[j];
    }
    return null;
  }
}
