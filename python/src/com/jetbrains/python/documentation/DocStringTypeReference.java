package com.jetbrains.python.documentation;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public class DocStringTypeReference extends PsiReferenceBase<PsiElement> {
  public DocStringTypeReference(PsiElement element, TextRange range) {
    super(element, range);
  }

  public boolean isSoft() {
    return true;
  }

  @Nullable
  public PsiElement resolve() {
    final String referencedName = getCanonicalText();
    ResolveProcessor processor = new ResolveProcessor(referencedName);

    PsiElement realContext = PyPsiUtils.getRealContext(myElement);

    PsiElement containingFile = realContext.getContainingFile();
    PsiElement element = PyResolveUtil.treeCrawlUp(processor, false, realContext, containingFile);

    PyBuiltinCache builtins_cache = PyBuiltinCache.getInstance(realContext);
    if (element == null) {
      PyFile bfile = builtins_cache.getBuiltinsFile();
      if (bfile != null) {
        element = bfile.getElementNamed(referencedName);
      }
    }
    if (element != null) {
      return element;
    }
    return null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return new Object[]{"str", "int", "basestring", "bool", "buffer", "bytearray", "complex", "dict", "tuple", "enumerate",
      "file", "float", "frozenset", "list", "long", "set", "object"};
  }
}
