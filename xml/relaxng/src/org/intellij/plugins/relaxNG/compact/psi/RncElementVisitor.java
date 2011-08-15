/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.compact.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;

public class RncElementVisitor extends PsiElementVisitor {

  @Override
  public final void visitElement(PsiElement element) {
    if (element instanceof RncElement) {
      visitElement((RncElement)element);
    } else {
      superVisitElement(element);
    }
  }

  protected void superVisitElement(PsiElement element) {
    super.visitElement(element);
  }

  public void visitElement(RncElement element) {
    super.visitElement(element);
  }

  public void visitInclude(RncInclude include) {
    visitElement(include);
  }

  public void visitDiv(RncDiv div) {
    visitElement(div);
  }

  public void visitRef(RncRef pattern) {
    visitElement(pattern);
  }

  public void visitParentRef(RncParentRef pattern) {
    visitElement(pattern);
  }

  public void visitDefine(RncDefine pattern) {
    visitElement(pattern);
  }

  public void visitGrammar(RncGrammar pattern) {
    visitElement(pattern);
  }

  public void visitName(RncName name) {
    visitElement(name);
  }

  public void visitAnnotation(RncAnnotation annotation) {
    visitElement(annotation);
  }

  public void visitExternalRef(RncExternalRef ref) {
    visitElement(ref);
  }
}