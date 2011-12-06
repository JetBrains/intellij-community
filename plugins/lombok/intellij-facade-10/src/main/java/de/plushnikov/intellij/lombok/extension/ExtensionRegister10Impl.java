package de.plushnikov.intellij.lombok.extension;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.refactoring.rename.RenameHandler;

/**
 * @author Plushnikov Michail
 */
public class ExtensionRegister10Impl implements ExtensionRegister {
  @Override
  public void registerTreeHandler() {
     ChangeUtil.registerTreeGenerator(new MyLightMethodTreeGenerator());
  }

  @Override
  public void registerRenameHandler() {
     Extensions.getRootArea().getExtensionPoint(RenameHandler.EP_NAME).
        registerExtension(new LombokElementRenameHandler(), LoadingOrder.FIRST);
  }
}
