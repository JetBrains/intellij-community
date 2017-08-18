package com.jetbrains.python.tools.sdkTools;

/**
 * SDK creation type
 * @author Ilya.Kazakevich
 */
public enum SdkCreationType {
  /**
   * SDK only (no packages nor skeletons)
   */
  EMPTY_SDK,
  /**
   * SDK + installed packages from syspath
   */
  SDK_PACKAGES_ONLY,
  /**
   * SDK + installed packages from syspath + skeletons
   */
  SDK_PACKAGES_AND_SKELETONS
}
