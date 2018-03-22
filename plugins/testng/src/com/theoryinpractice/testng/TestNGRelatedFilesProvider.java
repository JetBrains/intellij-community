// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng;

import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.theoryinpractice.testng.inspection.TestNGSearchScope;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestNGRelatedFilesProvider extends GotoRelatedProvider {

  @NotNull
  @Override
  public List<? extends GotoRelatedItem> getItems(@NotNull PsiElement context) {
    PsiClass psiClass = PsiTreeUtil.getParentOfType(context, PsiClass.class, false);
    if (psiClass != null) {
      final Project project = psiClass.getProject();
      while (psiClass != null && TestNGUtil.hasTest(psiClass) && PsiClassUtil.isRunnableClass(psiClass, true)) {
        final String qName = psiClass.getQualifiedName();
        if (qName != null) {
          final String packageQName = ((PsiJavaFile)psiClass.getContainingFile()).getPackageName();
          final String packageName = StringUtil.getShortName(packageQName);
          final String[] names;
          if (packageQName.length() > 0) {
            final String pName = packageName.length() > 0 ? packageName : packageQName;
            names = new String[]{qName, pName};
          }
          else {
            names = new String[]{qName};
          }
          final List<PsiElement> tags = new ArrayList<>();
          for (final String name : names) {
            PsiSearchHelper.getInstance(project)
              .processUsagesInNonJavaFiles(name, (file, startOffset, endOffset) -> {
                final PsiReference referenceAt = file.findReferenceAt(startOffset);
                if (referenceAt != null) {
                  if (packageQName.endsWith(name)) { //special package tag required
                    final XmlTag tag = PsiTreeUtil.getParentOfType(file.findElementAt(startOffset), XmlTag.class);
                    if (tag == null || !tag.getName().equals("package")) {
                      return true;
                    }
                    final XmlAttribute attribute = tag.getAttribute("name");
                    if (attribute == null) return true;
                    final String value = attribute.getValue();
                    if (value == null) return true;
                    if (!(value.equals(StringUtil.getQualifiedName(packageQName, "*")) || value.equals(packageQName))) return true;
                  }
                  tags.add(referenceAt.getElement());
                }
                return true;
              }, new TestNGSearchScope(project));
          }

          if (!tags.isEmpty()) {
            return GotoRelatedItem.createItems(tags, "TestNG");
          }
        }
        psiClass = PsiTreeUtil.getParentOfType(psiClass, PsiClass.class);
      }
    }
    return Collections.emptyList();
  }
}
