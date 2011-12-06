package de.plushnikov.intellij.lombok.extension;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.psi.impl.source.tree.TreeGenerator;
import com.intellij.refactoring.rename.RenameHandler;

/**
 * @author Plushnikov Michail
 */
public class ExtensionRegister11Impl implements ExtensionRegister {
  @Override
  public void registerTreeHandler() {
     Extensions.getRootArea().getExtensionPoint(TreeGenerator.EP_NAME).
        registerExtension(new MyLightMethodTreeGenerator());
  }

  @Override
  public void registerRenameHandler() {
     Extensions.getRootArea().getExtensionPoint(RenameHandler.EP_NAME).
        registerExtension(new LombokElementRenameHandler(), LoadingOrder.FIRST);
  }
}
