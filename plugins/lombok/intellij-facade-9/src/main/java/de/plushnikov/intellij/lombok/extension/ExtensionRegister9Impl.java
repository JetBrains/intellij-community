package de.plushnikov.intellij.lombok.extension;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.refactoring.rename.RenameHandler;

/**
 * @author Plushnikov Michail
 */
public class ExtensionRegister9Impl implements ExtensionRegister {

  private final LombokLightMethodTreeGenerator LOMBOK_LIGHT_METHOD_TREE_GENERATOR;
  private final LombokElementRenameHandler     LOMBOK_ELEMENT_RENAME_HANDLER;

  public ExtensionRegister9Impl() {
    LOMBOK_LIGHT_METHOD_TREE_GENERATOR = new LombokLightMethodTreeGenerator();
    LOMBOK_ELEMENT_RENAME_HANDLER = new LombokElementRenameHandler();
  }

  @Override
  public void registerTreeHandler() {
    ChangeUtil.registerTreeGenerator(LOMBOK_LIGHT_METHOD_TREE_GENERATOR);
  }

  public void unregisterTreeHandler() {

  }

  @Override
  public void registerRenameHandler() {
    Extensions.getRootArea().getExtensionPoint(RenameHandler.EP_NAME).
        registerExtension(LOMBOK_ELEMENT_RENAME_HANDLER, LoadingOrder.FIRST);
  }

  public void unregisterRenameHandler() {
    Extensions.getRootArea().getExtensionPoint(RenameHandler.EP_NAME).
        unregisterExtension(LOMBOK_ELEMENT_RENAME_HANDLER);
  }
}
