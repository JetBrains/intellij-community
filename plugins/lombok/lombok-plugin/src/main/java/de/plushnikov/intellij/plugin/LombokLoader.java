package de.plushnikov.intellij.plugin;

import de.plushnikov.intellij.lombok.extension.ExtensionRegister;
import de.plushnikov.intellij.lombok.extension.LombokExtensionRegisterFactory;

/**
 * Main application component, that loads Lombok support
 */
public class LombokLoader {
  public LombokLoader() {
    ExtensionRegister extensionRegister = LombokExtensionRegisterFactory.getInstance().createExtensionRegister();
    extensionRegister.registerRenameHandler();
    extensionRegister.registerTreeHandler();



  }
}
