package de.plushnikov.intellij.lombok.extension;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.BuildNumber;
import de.plushnikov.intellij.lombok.util.IntelliJVersionRangeUtil;

/**
 * @author Plushnikov Michail
 */
public class LombokExtensionRegisterFactory {
  private LombokExtensionRegisterFactory() {
  }

  private static ExtensionRegister ourInstance;

  public static ExtensionRegister getInstance() {
    if (null == ourInstance) {
      ourInstance = createExtensionRegister();
    }
    return ourInstance;
  }

  private static ExtensionRegister createExtensionRegister() {
    final BuildNumber buildNumber = ApplicationInfo.getInstance().getBuild();
    switch (IntelliJVersionRangeUtil.getIntelliJVersion(buildNumber)) {
      case INTELLIJ_8:
        throw new RuntimeException(String.format("This version (%s) of IntelliJ is not supported!", buildNumber.asString()));
      case INTELLIJ_9:
        return new ExtensionRegister9Impl();
      case INTELLIJ_10:
      case INTELLIJ_10_5:
        return new ExtensionRegister10Impl();
      case INTELLIJ_11:
      default:
        return new ExtensionRegister11Impl();
    }
  }
}
