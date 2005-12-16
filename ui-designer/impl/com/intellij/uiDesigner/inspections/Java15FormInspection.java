package com.intellij.uiDesigner.inspections;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.FileCheckingInspection;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.InspectionProfile;
import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.actions.ResetValueAction;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.quickFixes.FormInspectionTool;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

      final FormFileErrorCollector collector = new FormFileErrorCollector(file, manager);
      FormEditingUtil.iterate(rootContainer, new FormEditingUtil.ComponentVisitor() {
        public boolean visit(final IComponent component) {
          checkComponentProperties(module, component, collector);
          return true;
        }
      });
      return collector.result();
    }
    return null;
  }

  @Nullable
  public ErrorInfo[] checkComponent(@NotNull GuiEditor editor, @NotNull RadComponent component) {
    FormEditorErrorCollector collector = new FormEditorErrorCollector(editor, component);
    checkComponentProperties(component.getModule(), component, collector);
    return collector.result();
  }

  private void checkComponentProperties(Module module, final IComponent component, final FormErrorCollector collector) {
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    final PsiManager psiManager = PsiManager.getInstance(module.getProject());
    final PsiClass aClass = psiManager.findClass(component.getComponentClassName(), scope);
    if (aClass == null) {
      return;
    }

    for(final IProperty prop: component.getModifiedProperties()) {
      final PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, prop.getName(), false, true);
      if (Java15APIUsageInspection.isJava15APIUsage(getter)) {
        collector.addError(prop,
                           InspectionsBundle.message("inspection.1.5.problem.descriptor", "@since 1.5"),
                           new EditorQuickFixProvider() {
                             public QuickFix createQuickFix(GuiEditor editor, RadComponent component) {
                               return new RemovePropertyFix(editor, component, (Property) prop);
                             }
                           });
      }
    }
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
