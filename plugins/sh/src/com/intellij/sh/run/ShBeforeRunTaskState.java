// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.openapi.components.BaseState;
import com.intellij.util.xmlb.annotations.Attribute;
import kotlin.jvm.internal.MutablePropertyReference1Impl;
import kotlin.jvm.internal.Reflection;
import kotlin.properties.ReadWriteProperty;
import kotlin.reflect.KProperty;
import org.jetbrains.annotations.Nullable;

public class ShBeforeRunTaskState extends BaseState {

  private static final KProperty[] delegatedProperties = new KProperty[]{
    Reflection.mutableProperty1(
      new MutablePropertyReference1Impl(Reflection.getOrCreateKotlinClass(ShBeforeRunTaskState.class), "runConfigType",
                                        "getRunConfigType()Ljava/lang/String;")),
    Reflection.mutableProperty1(
      new MutablePropertyReference1Impl(Reflection.getOrCreateKotlinClass(ShBeforeRunTaskState.class), "runConfigName",
                                        "getRunConfigName()Ljava/lang/String;"))};

  private final ReadWriteProperty<BaseState, String> runConfigTypeDelegate;
  private final ReadWriteProperty<BaseState, String> runConfigNameDelegate;

  public ShBeforeRunTaskState() {
    this.runConfigTypeDelegate = string(null).provideDelegate(this, delegatedProperties[0]);
    this.runConfigNameDelegate = string(null).provideDelegate(this, delegatedProperties[1]);
  }

  @Attribute("RUN_CONFIG_TYPE")
  @Nullable
  public final String getRunConfigType() {
    return this.runConfigTypeDelegate.getValue(this, delegatedProperties[0]);
  }

  public final void setRunConfigType(@Nullable String var1) {
    this.runConfigTypeDelegate.setValue(this, delegatedProperties[0], var1);
  }

  @Attribute("RUN_CONFIG_NAME")
  @Nullable
  public final String getRunConfigName() {
    return this.runConfigNameDelegate.getValue(this, delegatedProperties[1]);
  }

  public final void setRunConfigName(@Nullable String var1) {
    this.runConfigNameDelegate.setValue(this, delegatedProperties[1], var1);
  }
}
