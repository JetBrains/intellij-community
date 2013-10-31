package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplateProvider;

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
