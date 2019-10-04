// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.html;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.XmlTextFilter;
import com.intellij.psi.meta.MetaDataContributor;
import com.intellij.psi.meta.MetaDataRegistrar;
import com.intellij.util.ReflectionUtil;
import com.intellij.xml.util.XmlUtil;
import org.intellij.plugins.relaxNG.compact.psi.RncDecl;
import org.intellij.plugins.relaxNG.compact.psi.RncFile;
import org.intellij.plugins.relaxNG.compact.psi.RncNsDecl;
import org.intellij.plugins.relaxNG.compact.psi.impl.RncDocument;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class RngHtml5MetaDataContributor implements MetaDataContributor {
  @Override
  public void contributeMetaData(@NotNull MetaDataRegistrar registrar) {
    registrar.registerMetaData(
      new AndFilter(
        new ClassFilter(RncDocument.class),
        new MyRncNamespaceFilter(XmlUtil.HTML_URI, XmlUtil.XHTML_URI)),
      RelaxedHtmlFromRngNSDescriptor.class
    );
  }

  private static class MyRncNamespaceFilter extends XmlTextFilter {
    MyRncNamespaceFilter(String... namespaces) {
      super(namespaces);
    }

    @Override
    public boolean isClassAcceptable(Class hintClass) {
      return ReflectionUtil.isAssignable(RncDocument.class, hintClass);
    }

    @Override
    public boolean isAcceptable(Object element, PsiElement context) {
      if (!(element instanceof RncDocument)) {
        return false;
      }

      final PsiFile file = ((RncDocument)element).getContainingFile();
      String namespace = null;
      if (file instanceof RncFile) {
        for (RncDecl decl : ((RncFile)file).getDeclarations()) {
          if (decl instanceof RncNsDecl) {
            namespace = decl.getDeclaredNamespace();
            break;
          }
        }
      }

      if (namespace != null) {
        for (String aMyValue : myValue) {
          if (aMyValue.equals(namespace)) return true;
        }
      }
      return false;
    }
  }
}
