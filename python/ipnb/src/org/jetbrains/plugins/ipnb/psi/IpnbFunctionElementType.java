package org.jetbrains.plugins.ipnb.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.stubs.PyFunctionElementType;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import org.jetbrains.annotations.NotNull;

public class IpnbFunctionElementType extends PyFunctionElementType {
  public IpnbFunctionElementType() {
    super("IPNB_FUNCTION");
  }

  @NotNull
  @Override
  public PsiElement createElement(@NotNull ASTNode node) {
    return new IpnbPyFunction(node);
  }

  @Override
  public PyFunction createPsi(@NotNull PyFunctionStub stub) {
    return new IpnbPyFunction(stub);
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    return false;
  }
}
