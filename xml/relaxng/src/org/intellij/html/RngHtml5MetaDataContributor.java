package org.intellij.html;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.XmlTextFilter;
import com.intellij.psi.meta.MetaDataContributor;
import com.intellij.psi.meta.MetaDataRegistrar;
import com.intellij.util.ReflectionCache;
import com.intellij.xml.util.XmlUtil;
import org.intellij.plugins.relaxNG.compact.psi.RncDecl;
import org.intellij.plugins.relaxNG.compact.psi.RncFile;
import org.intellij.plugins.relaxNG.compact.psi.RncNsDecl;
import org.intellij.plugins.relaxNG.compact.psi.impl.RncDocument;

/**
 * @author Eugene.Kudelevsky
 */
public class RngHtml5MetaDataContributor implements MetaDataContributor {
  @Override
  public void contributeMetaData(MetaDataRegistrar registrar) {
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

    public boolean isClassAcceptable(Class hintClass) {
      return ReflectionCache.isAssignable(RncDocument.class, hintClass);
    }

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
