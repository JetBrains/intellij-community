// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registers {@link DomFileDescription}.
 */
public final class DomFileMetaData implements PluginAware {
  static final ExtensionPointName<DomFileMetaData> EP_NAME = new ExtensionPointName<>("com.intellij.dom.fileMetaData");

  private transient PluginDescriptor pluginDescriptor;

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }

  /**
   * A {@link DomFileDescription} inheritor.
   */
  @Attribute("implementation")
  @RequiredElement
  public String implementation;

  /**
   * The unqualified root tag name addressed by this description.
   * Leave empty if the full tag set is unknown, then {@link DomFileDescription#isMyFile} will be used to determine file description applicability.
   */
  @Nullable
  @Attribute("rootTagName")
  public String rootTagName;

  /**
   * A version to be incremented when the logic determining the file description applicability (and hence, the set of suitable files) changes.
   */
  @Attribute("domVersion")
  public int domVersion;

  /**
   * Define this attribute if any DOM inside such files is stubbed (see {@link com.intellij.util.xml.Stubbed}). Otherwise,
   * set it to a version which should be incremented each time something affecting the stub structure changes.
   */
  @Nullable
  @Attribute("stubVersion")
  public Integer stubVersion;

  volatile DomFileDescription<?> lazyInstance;

  // created by reflection
  @SuppressWarnings("unused")
  public DomFileMetaData() {}

  @SuppressWarnings("deprecation")
  public DomFileMetaData(DomFileDescription<?> description) {
    lazyInstance = description;
    implementation = description.getClass().getName();
    rootTagName = description.acceptsOtherRootTagNames() ? null : description.getRootTagName();
    domVersion = description.getVersion();
    stubVersion = description.hasStubs() ? description.getStubVersion() : null;
  }

  // synchronized under DomApplicationComponent lock
  DomFileDescription<?> getDescription() {
    DomFileDescription<?> instance = lazyInstance;
    if (instance != null) {
      return instance;
    }

    instance = ApplicationManager.getApplication().instantiateClass(implementation, pluginDescriptor);
    if (Strings.isEmpty(rootTagName)) {
      if (!instance.acceptsOtherRootTagNames()) {
        throw new PluginException(
          implementation + " should either specify 'rootTagName' in XML, or return true from 'acceptsOtherRootTagNames'",
          pluginDescriptor.getPluginId());
      }
    }
    else if (!rootTagName.equals(instance.getRootTagName())) {
      throw new PluginException(implementation + " XML declaration should have '" + instance.getRootTagName() + "' for 'rootTagName'",
                                pluginDescriptor.getPluginId());
    }

    try {
      DomApplicationComponent.getInstance().initDescription(instance);
      lazyInstance = instance;
    }
    catch (ProcessCanceledException | PluginException e) {
      throw e;
    }
    catch (Exception e) {
      throw new PluginException(e, pluginDescriptor.getPluginId());
    }
    return instance;
  }

  public boolean hasStubs() {
    return stubVersion != null;
  }
}
