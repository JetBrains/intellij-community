package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class PostfixTemplatesManager implements ApplicationComponent {
  @NotNull private final PostfixTemplateProvider[] myTemplateProviders;

  public PostfixTemplatesManager(@NotNull PostfixTemplateProvider[] providers) {
    myTemplateProviders = providers;
  }

  public void getAvailableActions() {
    for (PostfixTemplateProvider myTemplateProvider : myTemplateProviders) {

    }
  }

  @Override
  public void initComponent() { }

  @Override
  public void disposeComponent() { }

  @NotNull
  @Override
  public String getComponentName() {
    return PostfixTemplatesManager.class.getTypeName();
  }
}
