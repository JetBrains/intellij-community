// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.RemoteSdkCredentialsHolder;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

public final class PyRemoteSdkCredentialsHolder extends RemoteSdkCredentialsHolder implements PyRemoteSdkCredentials {
  private static final String HELPERS_DIR = ".pycharm_helpers";
  private static final String SKELETONS_PATH = "SKELETONS_PATH";

  private String mySkeletonsPath;

  public PyRemoteSdkCredentialsHolder() {
    super(HELPERS_DIR);
  }

  public void setSkeletonsPath(String path) {
    mySkeletonsPath = path;
  }

  public String getSkeletonsPath() {
    return mySkeletonsPath;
  }


  @Override
  public @Nullable PyRemoteSdkCredentialsHolder clone() {
    try {
      final PyRemoteSdkCredentialsHolder copy = (PyRemoteSdkCredentialsHolder)super.clone();
      if (copy == null) {
        return null;
      }
      copyTo(copy);

      return copy;
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

  public void copyTo(PyRemoteSdkCredentialsHolder copy) {
    super.copyRemoteSdkCredentialsTo(copy);

    copy.setSkeletonsPath(getSkeletonsPath());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PyRemoteSdkCredentialsHolder data = (PyRemoteSdkCredentialsHolder)o;

    if (!super.equals(data)) return false;

    if (!StringUtil.equals(mySkeletonsPath, data.mySkeletonsPath)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + mySkeletonsPath.hashCode();
    return result;
  }

  @Override
  public void save(Element element) {
    super.save(element);
    element.setAttribute(SKELETONS_PATH, getSkeletonsPath());
  }

  @Override
  public void load(Element element) {
    super.load(element);
    setSkeletonsPath(element.getAttributeValue(SKELETONS_PATH));
  }
}

