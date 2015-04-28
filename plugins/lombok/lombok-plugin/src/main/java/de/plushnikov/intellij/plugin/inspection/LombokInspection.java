package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiTypeElement;
import de.plushnikov.intellij.plugin.extension.LombokProcessorExtensionPoint;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.processor.ValProcessor;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * @author Plushnikov Michail
 */
public class LombokInspection extends BaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(LombokInspection.class.getName());

  private final Map<String, Collection<Processor>> allProblemHandlers;

  private final ValProcessor valProcessor;

  public LombokInspection() {
    valProcessor = new ValProcessor();

    allProblemHandlers = new HashMap<String, Collection<Processor>>();
    for (Processor lombokInspector : LombokProcessorExtensionPoint.EP_NAME.getExtensions()) {
      Collection<Processor> inspectorCollection = allProblemHandlers.get(lombokInspector.getSupportedAnnotation());
      if (null == inspectorCollection) {
        inspectorCollection = new ArrayList<Processor>(2);
        allProblemHandlers.put(lombokInspector.getSupportedAnnotation(), inspectorCollection);
      }
      inspectorCollection.add(lombokInspector);

      LOG.debug(String.format("LombokInspection registered %s inspector", lombokInspector));
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
    return new LombokElementVisitor(holder);
  }

  private class LombokElementVisitor extends JavaElementVisitor {

    private final ProblemsHolder holder;

    public LombokElementVisitor(ProblemsHolder holder) {
      this.holder = holder;
    }

    @Override
    public void visitTypeElement(PsiTypeElement type) {
      super.visitTypeElement(type);

      valProcessor.verifyTypeElement(type, holder);
    }

    @Override
    public void visitAnnotation(PsiAnnotation annotation) {
      super.visitAnnotation(annotation);

      final String qualifiedName = annotation.getQualifiedName();
      if (StringUtils.isNotBlank(qualifiedName) && allProblemHandlers.containsKey(qualifiedName)) {
        final Collection<LombokProblem> problems = new HashSet<LombokProblem>();

        for (Processor inspector : allProblemHandlers.get(qualifiedName)) {
          problems.addAll(inspector.verifyAnnotation(annotation));
        }

        for (LombokProblem problem : problems) {
          holder.registerProblem(annotation, problem.getMessage(), problem.getHighlightType(), problem.getQuickFixes());
        }
      }
    }
  }
}
