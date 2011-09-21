package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementVisitor;
import de.plushnikov.intellij.lombok.problem.LombokProblem;
import de.plushnikov.intellij.lombok.processor.LombokProcessor;
import de.plushnikov.intellij.plugin.core.GenericServiceLocator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Plushnikov Michail
 */
public class LombokInspection extends BaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(LombokInspection.class.getName());

  private final Map<String, Collection<LombokProcessor>> allProblemHandlers;

  public LombokInspection() {
    allProblemHandlers = new HashMap<String, Collection<LombokProcessor>>();
    List<LombokProcessor> lombokInspectors = GenericServiceLocator.locateAll(LombokProcessor.class);
    for (LombokProcessor inspector : lombokInspectors) {
      Collection<LombokProcessor> inspectorCollection = allProblemHandlers.get(inspector.getSupportedAnnotation());
      if (null == inspectorCollection) {
        inspectorCollection = new ArrayList<LombokProcessor>(2);
        allProblemHandlers.put(inspector.getSupportedAnnotation(), inspectorCollection);
      }
      inspectorCollection.add(inspector);
    }
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Lombok annotations inspection";
  }

  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "Lombok";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAnnotation(PsiAnnotation annotation) {
        super.visitAnnotation(annotation);

        final String qualifiedName = annotation.getQualifiedName();
        if (null != qualifiedName && allProblemHandlers.containsKey(qualifiedName)) {
          for (LombokProcessor inspector : allProblemHandlers.get(qualifiedName)) {
            Collection<LombokProblem> problems = inspector.verifyAnnotation(annotation);
            for (LombokProblem problem : problems) {
              holder.registerProblem(annotation, problem.getMessage(), problem.getHighlightType(), problem.getQuickFixes());
            }
          }
        }
      }
    };
  }
}
