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
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.rest.validation.RestElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class RestReferenceTarget extends RestElement {
  public RestReferenceTarget(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "RestReferenceTarget:" + getNode().getElementType().toString();
  }

  public String getReferenceName(boolean quoted) {
    String text = getNode().getText();
    if ("__".equals(text))
      return text;

    text = text.replaceAll("\\\\([^\\\\]+)", "$1");
    if (text.startsWith("_`") && !quoted)
      return text.substring(2, text.length()-2);

    if (text.startsWith("_"))
      return text.substring(1, text.length()-1);

    if (text.startsWith("[#") && !quoted && text.length()>3)
      return text.substring(2, text.length()-1);

    if (text.startsWith("[") && !quoted)
      return text.substring(1, text.length()-1);

    return text;
  }
  public String getReferenceName() {
    return getReferenceName(true);
  }

  public boolean hasReference() {
    String text = getNode().getText();
    PsiFile file = getContainingFile();
    if ("__".equals(text)) {
      RestReference[] references = PsiTreeUtil.getChildrenOfType(file, RestReference.class);
      if (references != null) {
        for (RestReference ref : references) {
          if (ref.resolve() == this)
            return true;
        }
      }
      return false;
    }
    return true;
  }

  @Override
  protected void acceptRestVisitor(RestElementVisitor visitor) {
    visitor.visitReferenceTarget(this);
  }

}
