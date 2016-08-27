/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.intentions;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;

/**
 * @author Sang-Jin Park
 */
public class GroovyAddMavenDependencyQuickFix extends AddMavenDependencyQuickFix<GrReferenceElement> {

  public GroovyAddMavenDependencyQuickFix(GrReferenceElement ref) {
    super(ref);
  }

  public String getReferenceText() {
    GrReferenceElement result = myRef;
    while (true) {
      PsiElement parent = result.getParent();
      if (!(parent instanceof GrReferenceElement)) {
        break;
      }

      result = (GrReferenceElement)parent;
    }

    if (result.getQualifier() != null) {
      return result.getQualifier().getText() + "." + result.getReferenceName();
    }
    else {
      return result.getReferenceName();
    }
  }
}
