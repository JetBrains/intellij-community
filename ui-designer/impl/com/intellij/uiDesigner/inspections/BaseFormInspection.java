package com.intellij.uiDesigner.inspections;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.*;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.PsiPropertiesProvider;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.uiDesigner.quickFixes.FormInspectionTool;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class BaseFormInspection extends LocalInspectionTool implements FileCheckingInspection, FormInspectionTool {
  private String myInspectionKey;

  public BaseFormInspection(@NonNls String inspectionKey) {
    myInspectionKey = inspectionKey;
  }

  public String getGroupDisplayName() {
    return UIDesignerBundle.message("form.inspections.group");
  }

  public String getDisplayName() {
    return null;
  }

  @NonNls public String getShortName() {
    return myInspectionKey;
  }

  @Override public boolean isEnabledByDefault() {
    return true;
  }

  public boolean isActive(PsiElement psiRoot) {
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(psiRoot.getProject()).getInspectionProfile(psiRoot);
    HighlightDisplayKey key = HighlightDisplayKey.find(myInspectionKey);
    if (key == null) {
      return false;
    }
    return profile.isToolEnabled(key);
  }

  @Nullable public ProblemDescriptor[] checkFile(final PsiFile file, final InspectionManager manager, boolean isOnTheFly) {
    if (file.getFileType().equals(StdFileTypes.GUI_DESIGNER_FORM)) {
      final Module module = VfsUtil.getModuleForFile(file.getProject(), file.getVirtualFile());
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

      if (rootContainer.isInspectionSuppressed(getShortName(), null)) {
        return null;
      }
      final FormFileErrorCollector collector = new FormFileErrorCollector(file, manager);
      startCheckForm(rootContainer);
      FormEditingUtil.iterate(rootContainer, new FormEditingUtil.ComponentVisitor() {
        public boolean visit(final IComponent component) {
          if (!rootContainer.isInspectionSuppressed(getShortName(), component.getId())) {
            checkComponentProperties(module, component, collector);
          }
          return true;
        }
      });
      doneCheckForm(rootContainer);
      return collector.result();
    }
    return null;
  }

  public void startCheckForm(IRootContainer rootContainer) {
  }

  public void doneCheckForm(IRootContainer rootContainer) {
  }

  @Nullable
  public ErrorInfo[] checkComponent(@NotNull GuiEditor editor, @NotNull RadComponent component) {
    FormEditorErrorCollector collector = new FormEditorErrorCollector(editor, component);
    checkComponentProperties(component.getModule(), component, collector);
    return collector.result();
  }

  protected abstract void checkComponentProperties(Module module, IComponent component, FormErrorCollector collector);
}
