// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.scientific.figure.image;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.EditorTabbedContainer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.docking.DockableContent;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.scientific.figure.WithBinaryContent;
import com.jetbrains.python.scientific.figure.WithDockableContent;
import com.jetbrains.python.scientific.figure.base.FigureBase;
import com.jetbrains.python.scientific.figure.base.FigureUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static com.jetbrains.python.scientific.figure.FigureConstants.*;

public class ImageFigure extends FigureBase implements WithDockableContent {
  public static ImageFigure createDefault(BufferedImage image, Project project) {
    String simpleName = PLOT_DEFAULT_NAME + "." + DEFAULT_IMAGE_FORMAT;
    ImageVirtualFile virtualFile = new ImageVirtualFile(simpleName, image);
    return new ImageFigure(virtualFile, project);
  }

  private static TabInfo createTabInfo(ImageVirtualFile imageVirtualFile, Project project) {
    final JPanel panel = new MyContentPanel(imageVirtualFile, project);
    final TabInfo info = new TabInfo(panel);
    info.setTabColor(UIUtil.getPanelBackground());
    final BufferedImage image = imageVirtualFile.getImage();
    final Image after = FigureUtil.fit(image, THUMBNAIL_ICON_WIDTH, THUMBNAIL_ICON_HEIGHT);
    info.setIcon(new ImageIcon(after));
    info.setText(" ");
    return info;
  }


  private final TabInfo myTabInfo;
  private final Project myProject;

  public ImageFigure(ImageVirtualFile imageVirtualFile, Project project) {
    myProject = project;
    myTabInfo = createTabInfo(imageVirtualFile, project);
  }

  @Override
  public TabInfo getTabInfo() {
    return myTabInfo;
  }

  @Override
  public DockableContent createDockableContent() {
    Image img = JBTabsImpl.getComponentImage(myTabInfo);
    Presentation presentation = new Presentation(myTabInfo.getText());
    Dimension preferredSize = myTabInfo.getComponent().getPreferredSize();

    ImageVirtualFile file = ((MyContentPanel)myTabInfo.getComponent()).getVirtualFile();
    // make a copy because the original file will be disposed on removing from plot tool window
    ImageVirtualFile fileCopy = ImageVirtualFile.makeCopy(file);
    return new EditorTabbedContainer.DockableEditor(myProject, img, fileCopy, presentation, preferredSize, false);
  }


  private static class MyContentPanel extends JPanel implements WithBinaryContent, Disposable {
    private final ImageVirtualFile myVirtualFile;
    private FileEditor myEditor;

    private MyContentPanel(ImageVirtualFile virtualFile, Project project) {
      super(new BorderLayout());
      myVirtualFile = virtualFile;
      ApplicationManager.getApplication().invokeLater(() -> {
        //ImageFileEditorProvider#EDITOR_TYPE_ID
        final FileEditorProvider provider = FileEditorProviderManager.getInstance().getProvider("images");
        if (provider != null) {
          myEditor = provider.createEditor(project, myVirtualFile);
          add(myEditor.getComponent());
        }
      });

      setBackground(UIUtil.getEditorPaneBackground());
    }

    private ImageVirtualFile getVirtualFile() {
      return myVirtualFile;
    }

    @Override
    public byte[] getBytes() {
      return myVirtualFile.getContent();
    }

    @Override
    public void dispose() {
      Disposer.dispose(myVirtualFile);
      Disposer.dispose(myEditor);
    }
  }
}
