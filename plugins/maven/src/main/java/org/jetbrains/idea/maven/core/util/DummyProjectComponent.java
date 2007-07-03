package org.jetbrains.idea.maven.core.util;

import com.intellij.openapi.components.ProjectComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class DummyProjectComponent implements ProjectComponent {
  @NonNls private final String myComponentName;

  protected DummyProjectComponent(@NonNls final String componentName) {
    myComponentName = componentName;
  }

  protected DummyProjectComponent() {
    this(null);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    if (myComponentName != null) {
      return myComponentName;
    }
    final String name = getClass().getName();
    return name.substring(name.lastIndexOf(".") +1);
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
