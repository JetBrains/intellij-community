package com.intellij.uiDesigner.inspections;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionProfile;
import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwIntrospectedProperty;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.actions.ResetValueAction;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.quickFixes.FormInspectionTool;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public class Java15FormInspection implements FileCheckingInspection, FormInspectionTool {
  public boolean isActive(PsiElement psiRoot) {
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(psiRoot.getProject()).getProfile(psiRoot);
    HighlightDisplayKey key = HighlightDisplayKey.find("Since15");
    if (key == null) {
      return false;
    }
    return profile.isToolEnabled(key);
  }

  @Nullable public ProblemDescriptor[] checkFile(final PsiFile file, final InspectionManager manager, boolean isOnTheFly) {
    if (file.getFileType().equals(StdFileTypes.GUI_DESIGNER_FORM)) {
      final Module module = ModuleUtil.getModuleForFile(file.getProject(), file.getVirtualFile());
      if (module == null) {
        return null;
      }

      final LwRootContainer rootContainer;
      try {
        rootContainer = Utils.getRootContainer(file.getText(), new PsiPropertiesProvider(module));
      }
      catch (Exception e) {
        return null;
      }

      final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
      final PsiManager psiManager = PsiManager.getInstance(module.getProject());

      final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
      FormEditingUtil.iterate(rootContainer, new FormEditingUtil.ComponentVisitor() {
        public boolean visit(final IComponent component) {
          final PsiClass aClass = psiManager.findClass(component.getComponentClassName(), scope);
          if (aClass == null) {
            return true;
          }
          LwComponent lwComponent = (LwComponent) component;
          LwIntrospectedProperty[] props = lwComponent.getAssignedIntrospectedProperties();
          for(LwIntrospectedProperty prop: props) {
            final PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, prop.getName(), false, true);
            if (Java15APIUsageInspection.isJava15APIUsage(getter)) {
              problems.add(manager.createProblemDescriptor(file,
                                                           InspectionsBundle.message("inspection.1.5.problem.descriptor", "@since 1.5"),
                                                           (LocalQuickFix)null,
                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
            }
          }
          return true;
        }
      });
      return problems.toArray(new ProblemDescriptor[problems.size()]);
    }
    return null;
  }

  @Nullable
  public ErrorInfo[] checkComponent(@NotNull GuiEditor editor, @NotNull RadComponent component) {
    final Palette palette = Palette.getInstance(editor.getProject());
    IntrospectedProperty[] props = palette.getIntrospectedProperties(component.getComponentClass());
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(component.getModule());
    final PsiManager psiManager = PsiManager.getInstance(component.getModule().getProject());
    final PsiClass aClass = psiManager.findClass(component.getComponentClassName(), scope);
    if (aClass == null) {
      return null;
    }
    List<ErrorInfo> result = null;
    for(IntrospectedProperty prop: props) {
      if (component.isMarkedAsModified(prop)) {
        final PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, prop.getName(), false, true);
        if (Java15APIUsageInspection.isJava15APIUsage(getter)) {
          if (result == null) {
            result = new ArrayList<ErrorInfo>();
          }
          result.add(new ErrorInfo(prop.getName(),
                                   InspectionsBundle.message("inspection.1.5.problem.descriptor", "@since 1.5"),
                                   new QuickFix[] { new RemovePropertyFix(editor, component, prop) }));
        }
      }
    }

    return result == null ? null : result.toArray(new ErrorInfo[result.size()]);
  }

  private static class RemovePropertyFix extends QuickFix {
    private RadComponent myComponent;
    private Property myProperty;

    public RemovePropertyFix(GuiEditor editor, RadComponent component, Property property) {
      super(editor, UIDesignerBundle.message("remove.property.quickfix"));
      myProperty = property;
      myComponent = component;
    }


    public void run() {
      ResetValueAction.doResetValue(myComponent, myProperty, myEditor);
    }
  }
}
