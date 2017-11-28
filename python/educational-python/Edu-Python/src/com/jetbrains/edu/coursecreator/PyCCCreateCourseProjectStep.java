package com.jetbrains.edu.coursecreator;

import com.intellij.platform.DirectoryProjectGenerator;
import com.jetbrains.python.newProject.steps.PyCharmNewProjectStep;
import org.jetbrains.annotations.NotNull;

class PyCCCreateCourseProjectStep extends PyCharmNewProjectStep {

  public PyCCCreateCourseProjectStep() {
    super(new MyCustomization());
  }

  protected static class MyCustomization extends Customization {
    private final PyCCProjectGenerator myGenerator = new PyCCProjectGenerator();
    public MyCustomization() {
    }

    @NotNull
    @Override
    protected DirectoryProjectGenerator[] getProjectGenerators() {
      return new DirectoryProjectGenerator[] {};
    }

    @NotNull
    @Override
    protected DirectoryProjectGenerator createEmptyProjectGenerator() {
      return myGenerator;
    }
  }
}
