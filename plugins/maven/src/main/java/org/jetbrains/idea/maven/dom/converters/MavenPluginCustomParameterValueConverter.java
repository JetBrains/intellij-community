/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.WrappingConverter;
import com.intellij.util.xml.converters.values.GenericDomValueConvertersRegistry;
import org.jetbrains.annotations.NotNull;

public class MavenPluginCustomParameterValueConverter extends WrappingConverter {
  private final String myType;

  public MavenPluginCustomParameterValueConverter(@NotNull String type) {
    myType = PsiTypesUtil.boxIfPossible(type);
  }


  @Override
  public Converter getConverter(@NotNull GenericDomValue domElement) {
    Project project = domElement.getManager().getProject();

    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    PsiClass psiClass = psiFacade.findClass(myType, GlobalSearchScope.allScope(project));
    if (psiClass != null) {
      GenericDomValueConvertersRegistry convertersRegistry = MavenDomConvertersRegistry.getInstance().getConvertersRegistry();
      return convertersRegistry.getConverter(domElement, psiFacade.getElementFactory().createType(psiClass));
    }

    return null;
  }

}