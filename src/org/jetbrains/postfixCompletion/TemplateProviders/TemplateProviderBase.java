package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.openapi.components.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;

public abstract class TemplateProviderBase
  implements ApplicationComponent, PostfixTemplateProvider {

  @Override
  public void initComponent() { }

  @Override
  public void disposeComponent() { }

  @NotNull
  @Override
  public String getComponentName() {
    return this.getClass().getName();
  }
}
