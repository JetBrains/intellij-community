package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class PostfixTemplatesManager implements ApplicationComponent {
  private final ArrayList<PostfixTemplateProvider> myFoo;

  public PostfixTemplatesManager(Collection<PostfixTemplateProvider> providers) {
    myFoo = new ArrayList<>();
    myFoo.addAll(providers);
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
