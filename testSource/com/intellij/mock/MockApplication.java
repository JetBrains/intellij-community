package com.intellij.mock;

import com.intellij.ide.plugins.PluginDescriptor;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.util.ArrayUtil;

import java.awt.*;
import java.io.IOException;

import org.picocontainer.PicoContainer;

public class MockApplication extends UserDataHolderBase implements ApplicationEx {
  public String getName() {
    return "mock";
  }

  public void load(String path) throws IOException, InvalidDataException {
  }

  public boolean isInternal() {
    return false;
  }

  public boolean isDispatchThread() {
    return true;
  }

  public void setupIdeQueue(EventQueue queue) {
  }

  public String getComponentsDescriptor() {
    return null;
  }

  public void assertReadAccessAllowed() {
  }

  public void assertWriteAccessAllowed() {
  }

  public boolean isReadAccessAllowed() {
    return true;
  }

  public boolean isWriteAccessAllowed() {
    return true;
  }

  public boolean isUnitTestMode() {
    return true;
  }

  public boolean isAspectJSupportEnabled() {
    return true;
  }

  public boolean shouldLoadPlugins() {
    return false;
  }

  public BaseComponent getComponent(String name) {
    return null;
  }

  public <T> T getComponent(Class<T> interfaceClass) {
    return null;
  }

  public <T> T getComponent(Class<T> interfaceClass, T defaultImplementation) {
    return null;
  }

  public void runReadAction(Runnable action) {
    action.run();
  }

  public <T> T runReadAction(Computable<T> computation) {
    return computation.compute();
  }

  public void runWriteAction(Runnable action) {
    action.run();
  }

  public <T> T runWriteAction(Computable<T> computation) {
    return computation.compute();
  }

  public Object getCurrentWriteAction(Class actionClass) {
    return null;
  }

  public void assertIsDispatchThread() {
  }

  public void addApplicationListener(ApplicationListener listener) {
  }

  public void removeApplicationListener(ApplicationListener listener) {
  }

  public void saveAll() {
  }

  public void saveSettings() {
  }

  public void exit() {
  }

  public void dispose() {
  }

  public void assertReadAccessToDocumentsAllowed() {
  }

  public void doNotSave() {
  }

  public boolean shouldLoadPlugin(PluginDescriptor descriptor) {
    return false;
  }

  public boolean runProcessWithProgressSynchronously(Runnable process,
                                                     String progressTitle,
                                                     boolean canBeCanceled,
                                                     Project project) {
    return false;
  }

  public boolean runProcessWithProgressSynchronously(Runnable process,
                                                     String progressTitle,
                                                     boolean canBeCanceled,
                                                     Project project,
                                                     boolean smoothProgress) {
    return false;
  }

  public void invokeLater(Runnable runnable) {
  }

  public void invokeLater(Runnable runnable, ModalityState state) {
  }

  public void invokeAndWait(Runnable runnable, ModalityState modalityState) {
  }

  public long getStartTime() {
    return 0;
  }

  public long getIdleTime() {
    return 0;
  }

  public Class[] getComponentInterfaces() {
    return new Class[0];
  }

  public boolean hasComponent(Class interfaceClass) {
    return false;
  }

  public <T> T[] getComponents(Class<T> baseInterfaceClass) {
    return ArrayUtil.<T>emptyArray();
  }

  public PicoContainer getPicoContainer() {
    throw new UnsupportedOperationException("getPicoContainer is not implement in : " + getClass());
  }

  public ModalityState getCurrentModalityState() {
    return null;
  }

  public ModalityState getModalityStateForComponent(Component c) {
    return null;
  }

  public ModalityState getDefaultModalityState() {
    return null;
  }

  public ModalityState getNoneModalityState() {
    return null;
  }
}
