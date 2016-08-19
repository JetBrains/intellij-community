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

/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: 11/11/2006
 * Time: 16:15:10
 */
package com.theoryinpractice.testng;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupValueFactory;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.theoryinpractice.testng.inspection.DependsOnGroupsInspection;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TestNGReferenceContributor extends PsiReferenceContributor {
  private static PsiElementPattern.Capture<PsiLiteral> getElementPattern(String annotation) {
    return PlatformPatterns.psiElement(PsiLiteral.class).and(new FilterPattern(new TestAnnotationFilter(annotation)));
  }

  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(getElementPattern("dependsOnMethods"), new PsiReferenceProvider() {
      @NotNull
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new MethodReference[]{new MethodReference((PsiLiteral)element)};
      }
    });

    registrar.registerReferenceProvider(getElementPattern("dataProvider"), new PsiReferenceProvider() {
      @NotNull
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new DataProviderReference[]{new DataProviderReference((PsiLiteral)element)};
      }
    });
    registrar.registerReferenceProvider(getElementPattern("groups"), new PsiReferenceProvider() {
      @NotNull
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new GroupReference[]{new GroupReference(element.getProject(), (PsiLiteral)element)};
      }
    });
    registrar.registerReferenceProvider(getElementPattern("dependsOnGroups"), new PsiReferenceProvider() {
      @NotNull
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new GroupReference[]{new GroupReference(element.getProject(), (PsiLiteral)element)};
      }
    });
  }

  private static class MethodReference extends PsiReferenceBase<PsiLiteral> {

    public MethodReference(PsiLiteral element) {
      super(element, false);
    }

    @Nullable
    public PsiElement resolve() {
      @NonNls String val = getValue();
      final String methodName = StringUtil.getShortName(val);
      PsiClass cls = getDependsClass(val);
      if (cls != null) {
        PsiMethod[] methods = cls.findMethodsByName(methodName, true);
        for (PsiMethod method : methods) {
          if (TestNGUtil.hasTest(method, false) || TestNGUtil.hasConfig(method)) {
            return method;
          }
        }
      }
      return null;
    }

    @Nullable
    private PsiClass getDependsClass(String val) {
      final String className = StringUtil.getPackageName(val);
      final PsiLiteral element = getElement();
      return StringUtil.isEmpty(className) ? PsiUtil.getTopLevelClass(element)
                                           : JavaPsiFacade.getInstance(element.getProject()).findClass(className, element.getResolveScope());
    }

    @NotNull
    public Object[] getVariants() {
      List<Object> list = new ArrayList<>();
      @NonNls String val = getValue();
      int hackIndex = val.indexOf(CompletionUtil.DUMMY_IDENTIFIER);
      if (hackIndex > -1) {
        val = val.substring(0, hackIndex);
      }
      final String className = StringUtil.getPackageName(val);
      PsiClass cls = getDependsClass(val);
      if (cls != null) {
        final PsiMethod current = PsiTreeUtil.getParentOfType(getElement(), PsiMethod.class);
        final String configAnnotation = TestNGUtil.getConfigAnnotation(current);
        final PsiMethod[] methods = cls.getMethods();
        for (PsiMethod method : methods) {
          final String methodName = method.getName();
          if (current != null && methodName.equals(current.getName())) continue;
          if (configAnnotation == null && TestNGUtil.hasTest(method) || configAnnotation != null && AnnotationUtil.isAnnotated(method, configAnnotation, true)) {
            final String nameToInsert = StringUtil.isEmpty(className) ? methodName : StringUtil.getQualifiedName(cls.getQualifiedName(), methodName);
            list.add(LookupElementBuilder.create(nameToInsert));
          }
        }
      }
      return list.toArray();
    }
  }

  private static class GroupReference extends PsiReferenceBase<PsiLiteral> {
    private final Project myProject;

    public GroupReference(Project project, PsiLiteral element) {
      super(element, false);
      myProject = project;
    }

    @Nullable
    public PsiElement resolve() {
      return null;
    }

    @NotNull
    public Object[] getVariants() {
      List<Object> list = new ArrayList<>();

      InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();
      DependsOnGroupsInspection inspection = (DependsOnGroupsInspection)inspectionProfile.getUnwrappedTool(
        DependsOnGroupsInspection.SHORT_NAME, myElement);

      for (String groupName : inspection.groups) {
        list.add(LookupValueFactory.createLookupValue(groupName, null));
      }

      if (!list.isEmpty()) {
        return list.toArray();
      }
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
  }

  private static class TestAnnotationFilter implements ElementFilter {

    private final String myParameterName;

    public TestAnnotationFilter(@NotNull @NonNls String parameterName) {
      myParameterName = parameterName;
    }

    public boolean isAcceptable(Object element, PsiElement context) {
      PsiNameValuePair pair = PsiTreeUtil.getParentOfType(context, PsiNameValuePair.class, false, PsiMember.class, PsiStatement.class);
      if (null == pair) return false;
      if (!myParameterName.equals(pair.getName())) return false;
      PsiAnnotation annotation = PsiTreeUtil.getParentOfType(pair, PsiAnnotation.class);
      if (annotation == null) return false;
      if (!TestNGUtil.isTestNGAnnotation(annotation)) return false;
      return true;
    }

    public boolean isClassAcceptable(Class hintClass) {
      return PsiLiteral.class.isAssignableFrom(hintClass);
    }
  }
}
