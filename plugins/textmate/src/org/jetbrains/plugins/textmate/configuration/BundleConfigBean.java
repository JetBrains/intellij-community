package org.jetbrains.plugins.textmate.configuration;

import com.intellij.openapi.util.io.FileUtil;

public class BundleConfigBean implements Cloneable {

  public BundleConfigBean() {
  }

  public BundleConfigBean(String name, String path, Boolean enabled) {
    this.name = name;
    this.path = FileUtil.toSystemIndependentName(path);
    this.enabled = enabled;
  }

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
    if (name != null ? !name.equals(bean.name) : bean.name != null) return false;
    if (path != null ? !path.equals(bean.path) : bean.path != null) return false;

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
