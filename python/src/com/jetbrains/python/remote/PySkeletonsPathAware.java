package com.jetbrains.python.remote;

/**
 * @author traff
 * @deprecated
 */
public interface PySkeletonsPathAware {
  /**
   * Use RemoteSdkProperties.getPathMappings() instead
   * To be removed in IDEA 15
   * @deprecated
   */
  @Deprecated
  String getSkeletonsPath();

  /**
   * Use RemoteSdkProperties.getPathMappings() instead
   * To be removed in IDEA 15
   * @deprecated
   */
  @Deprecated
  void setSkeletonsPath(String path);
}
