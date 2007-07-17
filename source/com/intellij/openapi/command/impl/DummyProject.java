package com.intellij.openapi.command.impl;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomModel;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

/**
 * @author max
 */
public class DummyProject extends UserDataHolderBase implements Project {
  private static DummyProject ourInstance;

  public static Project getInstance() {
    if (ourInstance == null) {
      ourInstance = new DummyProject();
    }
    return ourInstance;
  }

  private DummyProject() {
  }

  public VirtualFile getProjectFile() {
    return null;
  }

  @NotNull
  public String getName() {
    return "";
  }

  @Nullable
  @NonNls
  public String getPresentableUrl() {
    return null;
  }

  @NotNull
  @NonNls
  public String getLocationHash() {
    return "dummy";
  }

  @NotNull
  @NonNls
  public String getLocation() {
    throw new UnsupportedOperationException("Method getLocation not implemented in " + getClass());
  }

  @NotNull
  public String getProjectFilePath() {
    return "";
  }

  public VirtualFile getWorkspaceFile() {
    return null;
  }

  @Nullable
  public VirtualFile getBaseDir() {
    return null;
  }

  public void save() {
  }

  public BaseComponent getComponent(String name) {
    return null;
  }

  public <T> T getComponent(Class<T> interfaceClass) {
    return null;
  }

  @NotNull
  public Class[] getComponentInterfaces() {
    return ArrayUtil.EMPTY_CLASS_ARRAY;
  }

  public boolean hasComponent(Class interfaceClass) {
    return false;
  }

  @NotNull
  public <T> T[] getComponents(Class<T> baseClass) {
    return (T[]) ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public PicoContainer getPicoContainer() {
    throw new UnsupportedOperationException("getPicoContainer is not implement in : " + getClass());
  }

  public <T> T getComponent(Class<T> interfaceClass, T defaultImplementation) {
    return null;
  }

  public boolean isDisposed() {
    return false;
  }

  @NotNull
  public ComponentConfig[] getComponentConfigurations() {
    return new ComponentConfig[0];
  }

  @Nullable
  public Object getComponent(final ComponentConfig componentConfig) {
    return null;
  }

  public boolean isOpen() {
    return false;
  }

  public boolean isInitialized() {
    return false;
  }

  public boolean isDefault() {
    return false;
  }

  @NotNull
  public PomModel getModel() {
    throw new UnsupportedOperationException("not implemented");
  }

  public GlobalSearchScope getAllScope() {
    return null;
  }

  public GlobalSearchScope getProjectScope() {
    return null;
  }

  public MessageBus getMessageBus() {
    return null;
  }

  public void dispose() {
  }

  public <T> T[] getExtensions(final ExtensionPointName<T> extensionPointName) {
    throw new UnsupportedOperationException("getExtensions()");
  }

  public ComponentConfig getConfig(Class componentImplementation) {
    throw new UnsupportedOperationException("Method getConfig not implemented in " + getClass());
  }
}
