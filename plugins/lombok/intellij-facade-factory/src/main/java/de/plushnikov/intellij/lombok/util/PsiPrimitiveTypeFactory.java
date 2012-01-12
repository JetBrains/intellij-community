package de.plushnikov.intellij.lombok.util;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.BuildNumber;

/**
 * @author Plushnikov Michail
 */
public class PsiPrimitiveTypeFactory {
  private PsiPrimitiveTypeFactory() {
  }

  private static PsiPrimitiveTypeUtil ourInstance;

  public static PsiPrimitiveTypeUtil getInstance() {
    if (null == ourInstance) {
      ourInstance = createUtil();
    }
    return ourInstance;
  }

  private static PsiPrimitiveTypeUtil createUtil() {
    final BuildNumber buildNumber = ApplicationInfo.getInstance().getBuild();
    switch (IntelliJVersionRangeUtil.getIntelliJVersion(buildNumber)) {
      case INTELLIJ_8:
        throw new RuntimeException(String.format("This version (%s) of IntelliJ is not supported!", buildNumber.asString()));
      case INTELLIJ_9:
        return new PsiPrimitiveTypeUtil9Impl();
      case INTELLIJ_10:
      case INTELLIJ_10_5:
        return new PsiPrimitiveTypeUtil10Impl();
      case INTELLIJ_11:
      default:
        return new PsiPrimitiveTypeUtil11Impl();
    }
  }
}
