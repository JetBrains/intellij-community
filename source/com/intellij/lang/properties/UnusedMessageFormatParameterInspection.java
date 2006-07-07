package com.intellij.lang.properties;

import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

/**
 * User: anna
 * Date: 07-Sep-2005
 */
public class UnusedMessageFormatParameterInspection extends BaseLocalInspectionTool {
  public String getGroupDisplayName() {
    return PropertiesBundle.message("properties.files.inspection.group.display.name");
  }

  public String getDisplayName() {
    return PropertiesBundle.message("unused.message.format.parameter.display.name");
  }

  @NonNls
  public String getShortName() {
    return "UnusedMessageFormatParameter";
  }

  @Nullable
  public ProblemDescriptor[] checkFile(PsiFile file, InspectionManager manager, boolean isOnTheFly) {
    if (!(file instanceof PropertiesFile)) return null;
    PropertiesFile propertiesFile = (PropertiesFile)file;
    final List<Property> properties = propertiesFile.getProperties();
    List<ProblemDescriptor> problemDescriptors = new ArrayList<ProblemDescriptor>();
    for (Property property : properties) {
      @NonNls String name = property.getName();
      if (name != null && name.startsWith("log4j")) continue;
      String value = property.getValue();
      Set<Integer> parameters = new HashSet<Integer>();
      if (value != null) {
        int index = value.indexOf('{');
        while (index != -1) {
          value = value.substring(index + 1);
          final int comma = value.indexOf(',');
          final int brace = value.indexOf('}');
          if (brace == -1) break; //misformatted string
          if (comma == -1) {
            index = brace;
          }
          else {
            index = Math.min(comma, brace);
          }
          try {
            parameters.add(new Integer(value.substring(0, index)));
          }
          catch (NumberFormatException e) {
            break;
          }
          index = value.indexOf('{');
        }
        for (Integer integer : parameters) {
          for (int i = 0; i < integer.intValue(); i++) {
            if (!parameters.contains(new Integer(i))) {
              ASTNode[] nodes = property.getNode().getChildren(null);
              PsiElement valElement = nodes.length < 3 ? property : nodes[2].getPsi();
              problemDescriptors.add(manager.createProblemDescriptor(valElement, PropertiesBundle.message(
                "unused.message.format.parameter.problem.descriptor", integer.toString(), Integer.toString(i)),
                                                                     (LocalQuickFix[])null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
              break;
            }
          }
        }
      }
    }
    return problemDescriptors.isEmpty() ? null : problemDescriptors.toArray(new ProblemDescriptor[problemDescriptors.size()]);
  }
}
