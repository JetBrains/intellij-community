package org.jetbrains.plugins.textmate.configuration;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;

import java.util.Objects;

public class BundleConfigBean implements Cloneable {

  public BundleConfigBean() {
  }

  public BundleConfigBean(String name, String path, Boolean enabled) {
    this.name = name;
    this.path = FileUtil.toSystemIndependentName(path);
    this.enabled = enabled;
  }

  @NlsSafe
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public BundleConfigBean copy() {
    return new BundleConfigBean(name, path, enabled);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BundleConfigBean bean = (BundleConfigBean)o;

    if (enabled != bean.enabled) return false;
    if (!Objects.equals(name, bean.name)) return false;
    if (!Objects.equals(path, bean.path)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (path != null ? path.hashCode() : 0);
    result = 31 * result + (enabled ? 1 : 0);
    return result;
  }

  private String name;
  private String path;
  private boolean enabled = true;
}
