package de.plushnikov.intellij.plugin;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.BuildNumber;
import de.plushnikov.intellij.lombok.extension.ExtensionRegister;
import de.plushnikov.intellij.lombok.extension.LombokExtensionRegisterFactory;

/**
 * Main application component, that loads Lombok support
 */
public class LombokLoader implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(LombokLoader.class.getName());

  public LombokLoader() {

  }

  @NotNull
  @Override
  public String getComponentName() {
    return "Lombok plugin for IntelliJ";
  }

  @Override
  public void initComponent() {
    final BuildNumber buildNumber = ApplicationInfo.getInstance().getBuild();
    LOG.info("Lombok plugin started for IntelliJ IDEA " + buildNumber.asString());

    ExtensionRegister extensionRegister = LombokExtensionRegisterFactory.getInstance();
    extensionRegister.registerRenameHandler();
    extensionRegister.registerTreeHandler();
  }

  @Override
  public void disposeComponent() {
    ExtensionRegister extensionRegister = LombokExtensionRegisterFactory.getInstance();

    extensionRegister.unregisterRenameHandler();
    extensionRegister.unregisterTreeHandler();
  }
}
