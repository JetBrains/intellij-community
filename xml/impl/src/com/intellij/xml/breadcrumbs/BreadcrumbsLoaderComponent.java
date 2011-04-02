
package com.intellij.xml.breadcrumbs;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author spleaner
 */
public class BreadcrumbsLoaderComponent extends AbstractProjectComponent {  

  public BreadcrumbsLoaderComponent(@NotNull final Project project) {
    super(project);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "HtmlBreadcrumbsComponent";
  }

  public void initComponent() {
    myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerListener());
  }

  private static boolean isEnabled() {
    final WebEditorOptions webEditorOptions = WebEditorOptions.getInstance();
    return webEditorOptions.isBreadcrumbsEnabled() || webEditorOptions.isBreadcrumbsEnabledInXml();
  }

  private static class MyFileEditorManagerListener extends FileEditorManagerAdapter {
    public void fileOpened(final FileEditorManager source, final VirtualFile file) {
      if (isEnabled() && isSuitable(source.getProject(), file)) {
        final FileEditor[] fileEditors = source.getEditors(file);
        for (final FileEditor fileEditor : fileEditors) {
          if (fileEditor instanceof TextEditor) {
            final BreadcrumbsXmlWrapper wrapper = new BreadcrumbsXmlWrapper(((TextEditor)fileEditor).getEditor());
            final JComponent c = wrapper.getComponent();
            source.addTopComponent(fileEditor, c);

            Disposer.register(fileEditor, wrapper);
            Disposer.register(fileEditor, new Disposable() {
              public void dispose() {
                source.removeTopComponent(fileEditor, c);
              }
            });
          }
        }
      }
    }

    private static boolean isSuitable(final Project project, final VirtualFile file) {
      if (file instanceof HttpVirtualFile) {
        return false;
      }

      final FileViewProvider provider = PsiManager.getInstance(project).findViewProvider(file);

      return provider != null
             && hasNonEmptyHtml(provider)
             && BreadcrumbsXmlWrapper.findInfoProvider(provider) != null;
    }

    public static boolean hasNonEmptyHtml(FileViewProvider viewProvider) {
      final List<PsiFile> files = viewProvider.getAllFiles();

      if (files.size() < 2) return true; // There is only HTML Language

      for (PsiFile file : files) {
        if (file.getLanguage() == HTMLLanguage.INSTANCE && file instanceof XmlFile) {
          final XmlDocument xml = ((XmlFile)file).getDocument();
          return xml != null && xml.getRootTag() != null;
        }
      }
      return false;
    }
  }
}
