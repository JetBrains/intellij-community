package com.intellij.execution.junit;

import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.impl.RunnerAndConfigurationSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

import java.util.Comparator;

public abstract class RuntimeConfigurationProducer implements Comparable {
  public static final Comparator COMPARATOR = new ProducerComparator();
  protected static final int PREFERED = -1;
  private final ConfigurationFactory myConfigurationFactory;
  private RunnerAndConfigurationSettings myConfiguration;

  public RuntimeConfigurationProducer(final ConfigurationType configurationType) {
    myConfigurationFactory = configurationType.getConfigurationFactories()[0];
  }

  public RuntimeConfigurationProducer createProducer(final Location location, final ConfigurationContext context) {
    final RuntimeConfigurationProducer result = clone();
    result.myConfiguration = location != null ? result.createConfigurationByElement(location, context) : null;
    return result;
  }

  public abstract PsiElement getSourceElement();

  public RunnerAndConfigurationSettings getConfiguration() {
    return myConfiguration;
  }

  protected abstract RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context);

  public RuntimeConfigurationProducer clone() {
    try {
      return (RuntimeConfigurationProducer)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public RunnerAndConfigurationSettings cloneTemplateConfiguration(final Project project, final ConfigurationContext context) {
    if (context != null) {
      final RunnerAndConfigurationSettings original = context.getOriginalConfiguration(myConfigurationFactory.getType());
      if (original != null) return original.clone();
    }
    return RunManager.getInstance(project).createConfiguration("", myConfigurationFactory);
  }

  public static PsiMethod getContainingMethod(PsiElement element) {
    while (element != null)
      if (element instanceof PsiMethod) break;
      else element = element.getParent();
    return (PsiMethod) element;
  }

  private static class ProducerComparator implements Comparator {
    public int compare(final Object o1, final Object o2) {
      final RuntimeConfigurationProducer producer1 = (RuntimeConfigurationProducer)o1;
      final RuntimeConfigurationProducer producer2 = (RuntimeConfigurationProducer)o2;
      final PsiElement psiElement1 = producer1.getSourceElement();
      final PsiElement psiElement2 = producer2.getSourceElement();
      if (doesContains(psiElement1, psiElement2)) return -PREFERED;
      if (doesContains(psiElement2, psiElement1)) return PREFERED;
      return producer1.compareTo(producer2);
    }

    private boolean doesContains(final PsiElement container, PsiElement element) {
      while ((element = element.getParent()) != null)
        if (container.equals(element)) return true;
      return false;
    }
  }
}
