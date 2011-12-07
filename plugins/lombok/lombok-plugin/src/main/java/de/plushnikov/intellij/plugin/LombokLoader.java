package de.plushnikov.intellij.plugin;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.BuildNumber;
import de.plushnikov.intellij.lombok.extension.ExtensionRegister;
import de.plushnikov.intellij.lombok.extension.LombokExtensionRegisterFactory;

/**
 * Main application component, that loads Lombok support
 */
public class LombokLoader {
  private static final Logger LOG = Logger.getInstance(LombokLoader.class.getName());

  public LombokLoader() {
    final BuildNumber buildNumber = ApplicationInfo.getInstance().getBuild();
    LOG.info("Lombok plugin started for IntelliJ IDEA " + buildNumber.asString());

    ExtensionRegister extensionRegister = LombokExtensionRegisterFactory.getInstance().createExtensionRegister();
    extensionRegister.registerRenameHandler();
    extensionRegister.registerTreeHandler();
  }
}
