package com.intellij.ide.browsers;

import com.intellij.ide.DataManager;
import com.intellij.ide.browsers.impl.WebBrowserServiceImpl;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.util.Url;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.AncestorEvent;

public class StartBrowserPanel {
  private JCheckBox myStartBrowserCheckBox;
  private JComponent myBrowserComboBox;

  private JCheckBox myStartJavaScriptDebuggerCheckBox;

  private TextFieldWithBrowseButton myUrlField;
  private BrowserSelector myBrowserSelector;

  private JPanel myRoot;

  public StartBrowserPanel() {
    myStartJavaScriptDebuggerCheckBox.setVisible(JavaScriptDebuggerStarter.Util.EP_NAME.getExtensions().length > 0);
    myRoot.addAncestorListener(new AncestorListenerAdapter() {
      @Override
      public void ancestorAdded(AncestorEvent event) {
        Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myUrlField));
        assert project != null;
        setupUrlField(myUrlField, project);
      }
    });
  }

  @NotNull
  public JPanel getComponent() {
    return myRoot;
  }

  @NotNull
  public String getUrl() {
    String url = myUrlField.getText();
    if (!url.isEmpty() && !URLUtil.containsScheme(url)) {
      return VirtualFileManager.constructUrl(StandardFileSystems.HTTP_PROTOCOL, url);
    }
    return url;
  }

  public void setUrl(@Nullable String url) {
    myUrlField.setText(StringUtil.notNullize(url));
  }

  public void clearBorder() {
    myRoot.setBorder(null);
  }

  public boolean isSelected() {
    return myStartBrowserCheckBox.isSelected();
  }

  public void setSelected(boolean value) {
    myStartBrowserCheckBox.setSelected(value);
  }

  public JCheckBox getStartJavaScriptDebuggerCheckBox() {
    return myStartJavaScriptDebuggerCheckBox;
  }

  public BrowserSelector getBrowserSelector() {
    return myBrowserSelector;
  }

  private void createUIComponents() {
    myBrowserSelector = new BrowserSelector(true);
    myBrowserComboBox = myBrowserSelector.getMainComponent();
    if (UIUtil.isUnderAquaLookAndFeel()) {
      myBrowserComboBox.setBorder(new EmptyBorder(3, 0, 0, 0));
    }
  }

  private static Url virtualFileToUrl(VirtualFile file, Project project) {
    PsiFile psiFile;
    AccessToken token = ReadAction.start();
    try {
      psiFile = PsiManager.getInstance(project).findFile(file);
    }
    finally {
      token.finish();
    }
    return psiFile != null && !(psiFile instanceof PsiBinaryFile) ? WebBrowserServiceImpl.getUrlForContext(psiFile) : null;
  }

  public static void setupUrlField(@NotNull TextFieldWithBrowseButton field, @NotNull final Project project) {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return HtmlUtil.isHtmlFile(file) || virtualFileToUrl(file, project) != null;
      }
    };
    descriptor.setTitle(XmlBundle.message("javascript.debugger.settings.choose.file.title"));
    descriptor.setDescription(XmlBundle.message("javascript.debugger.settings.choose.file.subtitle"));
    //descriptor.setShowFileSystemRoots(false);
    descriptor.setRoots(ProjectRootManager.getInstance(project).getContentRoots());

    field.addBrowseFolderListener(new TextBrowseFolderListener(descriptor, project) {
      @NotNull
      @Override
      protected String chosenFileToResultingText(@NotNull VirtualFile chosenFile) {
        return virtualFileToUrl(chosenFile, project).toDecodedForm();
      }
    });
  }
}