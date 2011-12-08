package de.plushnikov.intellij.lombok.extension;

/**
 * @author Plushnikov Michail
 */
public interface ExtensionRegister {
  void registerTreeHandler();

  void registerRenameHandler();

  void unregisterTreeHandler();

  void unregisterRenameHandler();
}
