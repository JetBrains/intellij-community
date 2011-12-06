package de.plushnikov.intellij.lombok.extension;

/**
 * @author Plushnikov Michail
 */
public class LombokExtensionRegisterFactory {
  private static LombokExtensionRegisterFactory ourInstance = new LombokExtensionRegisterFactory();

  public static LombokExtensionRegisterFactory getInstance() {
    return ourInstance;
  }

  private LombokExtensionRegisterFactory() {
  }

  public ExtensionRegister createExtensionRegister() {
    return new ExtensionRegister10Impl();
  }
}
