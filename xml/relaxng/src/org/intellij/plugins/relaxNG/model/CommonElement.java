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

package org.intellij.plugins.relaxNG.model;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 17.09.2007
 */
public interface CommonElement<E extends PsiElement> {
  void accept(Visitor visitor);

  void acceptChildren(Visitor visitor);

  @Nullable
  E getPsiElement();

  static abstract class Visitor {
    public void visitElement(CommonElement pattern) {
    }

    public void visitPattern(Pattern pattern) {
      visitElement(pattern);
    }

    public void visitGrammar(Grammar pattern) {
      visitElement(pattern);
    }

    public void visitDefine(Define define) {
      visitElement(define);
    }

    public void visitRef(Ref ref) {
      visitElement(ref);
    }

    public void visitDiv(Div div) {
      visitElement(div);
    }

    public void visitInclude(Include inc) {
      visitElement(inc);
    }
  }
}
