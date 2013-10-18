package com.jetbrains.python.psi.resolve;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.references.PyReferenceImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User : ktisha
 */
public class PythonBuiltinReferenceResolveProvider implements PyReferenceResolveProvider {
  @NotNull
  @Override
  public List<RatedResolveResult> resolveName(@NotNull PyQualifiedExpression element,
                                              @NotNull List<PsiElement> definers) {
    final List<RatedResolveResult> result = new ArrayList<RatedResolveResult>();
    final PsiElement realContext = PyPsiUtils.getRealContext(element);
    final String referencedName = element.getReferencedName();
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(realContext);
    // ...as a builtin symbol
    final PyFile bfile = builtinCache.getBuiltinsFile();
    if (bfile != null && !PyUtil.isClassPrivateName(referencedName)) {
      PsiElement resultElement = bfile.getElementNamed(referencedName);
      if (resultElement == null && "__builtins__".equals(referencedName)) {
        resultElement = bfile; // resolve __builtins__ reference
      }
      if (resultElement != null)
        result.add(new ImportedResolveResult(resultElement, PyReferenceImpl.getRate(resultElement), definers));
    }
    return result;
  }
}
