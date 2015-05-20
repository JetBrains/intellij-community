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
package org.jetbrains.idea.maven.javadoc;/*
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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.javadoc.CustomJavadocTagProvider;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MojoClassAnnotationTagProvider implements CustomJavadocTagProvider {

  private static final String[] ANNOTATION_NAMES = {
      "goal",
      "requiresDependencyResolution",
      "requiresProject",
      "requiresReports",
      "aggregator",
      "requiresOnline",
      "requiresDirectInvocation",
      "phase",
      "execute"
  };

  @Override
  public List<JavadocTagInfo> getSupportedTags() {
    return ContainerUtil.map(ANNOTATION_NAMES, new Function<String, JavadocTagInfo>() {
      @Override
      public JavadocTagInfo fun(String name) {
        return new MojoAnnotationInfo(name);
      }
    });
  }

}

class MojoAnnotationInfo implements JavadocTagInfo {

  private static final String BASE_CLASS = "org.apache.maven.plugin.Mojo";
  private final String myName;

  public MojoAnnotationInfo(@NotNull String name) {
    myName = name;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isInline() {
    return false;
  }

  @Override
  public boolean isValidInContext(PsiElement element) {
    if (element instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)element;
      return InheritanceUtil.isInheritor(psiClass, BASE_CLASS);
    }
    return false;
  }

  @Override
  public Object[] getPossibleValues(PsiElement context, PsiElement place, String prefix) {
    return null;
  }

  @Nullable
  @Override
  public String checkTagValue(PsiDocTagValue value) {
    return null;
  }

  @Nullable
  @Override
  public PsiReference getReference(PsiDocTagValue value) {
    return null;
  }
}



