package org.jetbrains.plugins.ipnb.editor.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

public class IpnbConvertFromPythonAction extends AnAction {
  public IpnbConvertFromPythonAction() {
    super(PythonIcons.Python.IpythonNotebook);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final DataContext context = event.getDataContext();
    final VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(context);
    final Project project = CommonDataKeys.PROJECT.getData(context);
    convertFromPythonScript(virtualFile, project);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(true);
    e.getPresentation().setVisible(true);

    final DataContext context = e.getDataContext();
    final PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(context);
    if (!(psiFile instanceof PyFile)) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
    }
    final VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(context);
    final Project project = CommonDataKeys.PROJECT.getData(context);
    if (virtualFile == null || project == null) {
      e.getPresentation().setEnabled(false);
    }
  }

  private static void convertFromPythonScript(VirtualFile virtualFile, Project project) {
    Module module = ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(virtualFile);
    if (module == null) return;

    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk == null) {
      return;
    }
    final String homePath = sdk.getHomePath();
    if (homePath == null) {
      return;
    }

    PySdkUtil.getProcessOutput(virtualFile.getParent().getPath(), new String[]{homePath,
      PythonHelpersLocator.getHelperPath("py2ipnb_converter.py"), virtualFile.getPath()});
    VfsUtil.markDirtyAndRefresh(true, false, true, virtualFile.getParent());
  }
}
