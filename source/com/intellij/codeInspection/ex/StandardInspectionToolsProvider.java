package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.codeInsight.i18n.StringI18nInspection;

/**
 * @author max
 */
public class StandardInspectionToolsProvider implements InspectionToolProvider, ApplicationComponent {
  public String getComponentName() {
    return "StandardInspectionToolsProvider";
  }

  public void initComponent() { }

  public void disposeComponent() {

  }

  public Class[] getInspectionClasses() {
    return new Class[] {
      com.intellij.codeInspection.deadCode.DeadCodeInspection.class,
      com.intellij.codeInspection.visibility.VisibilityInspection.class,
      com.intellij.codeInspection.canBeStatic.CanBeStaticInspection.class,
      com.intellij.codeInspection.canBeFinal.CanBeFinalInspection.class,
      com.intellij.codeInspection.unusedParameters.UnusedParametersInspection.class,
      com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection.class,
      com.intellij.codeInspection.unusedReturnValue.UnusedReturnValue.class,
      com.intellij.codeInspection.sameReturnValue.SameReturnValueInspection.class,
      com.intellij.codeInspection.emptyMethod.EmptyMethodInspection.class,
      com.intellij.codeInspection.unneededThrows.UnneededThrows.class,

      com.intellij.codeInspection.dataFlow.DataFlowInspection.class,
      com.intellij.codeInspection.defUse.DefUseInspection.class,
      com.intellij.codeInspection.redundantCast.RedundantCastInspection.class,
      com.intellij.codeInspection.miscGenerics.RedundantTypeArgsInspection.class,
      com.intellij.codeInspection.miscGenerics.SuspiciousCollectionsMethodCallsInspection.class,
      com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal.class,

      com.intellij.codeInspection.javaDoc.JavaDocInspection.class,
      com.intellij.codeInspection.deprecation.DeprecationInspection.class,
      com.intellij.codeInspection.equalsAndHashcode.EqualsAndHashcode.class,
      com.intellij.codeInspection.ejb.EJBInspection.class,

      StringI18nInspection.class,

    };
  }
}
