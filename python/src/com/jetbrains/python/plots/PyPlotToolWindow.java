/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jetbrains.python.plots;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.EditorTabbedContainer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.testFramework.BinaryLightVirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.DockableContent;
import com.intellij.ui.docking.DragSession;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.DefaultEditorTabsPainter;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class PyPlotToolWindow extends JPanel implements DumbAware {
  public static final String PLOT_DEFAULT_NAME = "myplot";
  public static final String PLOT_FORMAT = "png";
  private final static int ourPreviewSize = 80;

  private final JBEditorTabs myTabs;
  private final Project myProject;

  private static final Logger LOG = Logger.getInstance(PyPlotToolWindow.class);
  private MyDockContainer myDockContainer;

  public PyPlotToolWindow(final Project project) {
    super(new BorderLayout());
    myProject = project;
    myTabs = new MyTabs(myProject);
    myTabs.setTabsPosition(JBTabsPosition.right);
    myTabs.setPopupGroup(new DefaultActionGroup(new SaveAsFileAction()), ActionPlaces.UNKNOWN, true);
    myTabs.setTabDraggingEnabled(true);
    add(myTabs);
  }

  public void onMessage(int width, byte[] raw) {
    final PyPlotVirtualFile plotVirtualFile = new PyPlotVirtualFile(width, raw);
    final TabInfo info = createTabInfo(plotVirtualFile);
    myTabs.addTab(info);
    ApplicationManager.getApplication().invokeLater(() -> myTabs.select(info, true));
  }

  @NotNull
  private TabInfo createTabInfo(@NotNull final PyPlotVirtualFile plotVirtualFile) {
    final JPanel panel = new MyPlotPanel(plotVirtualFile);
    final TabInfo info = new TabInfo(panel);
    info.setTabColor(UIUtil.getPanelBackground());
    final BufferedImage image = plotVirtualFile.getImage();
    final Image after = image.getScaledInstance(64, 48, Image.SCALE_SMOOTH);
    info.setIcon(new ImageIcon(after));
    info.setText(" ");
    info.setTabLabelActions(new DefaultActionGroup(new ClosePlotAction(info)), ActionPlaces.UNKNOWN);
    info.setDragOutDelegate(new MyDragOutDelegate());
    return info;
  }

  public void init(ToolWindow toolWindow) {
    final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    Content content = contentFactory.createContent(this, "Plots", false);
    content.setCloseable(false);
    toolWindow.getContentManager().addContent(content);

    if (myDockContainer == null) {
      myDockContainer = new MyDockContainer(toolWindow);
      Disposer.register(myProject, myDockContainer);
      DockManager.getInstance(myProject).register(myDockContainer);
    }
  }

  public static PyPlotToolWindow getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, PyPlotToolWindow.class);
  }

  private static class MyTabs extends JBEditorTabs {
    public MyTabs(Project project) {
      super(project, ActionManager.getInstance(), IdeFocusManager.findInstance(), project);
      myDefaultPainter = new DefaultEditorTabsPainter(this) {
        @Override
        public Color getBackgroundColor() {
          return JBColor.LIGHT_GRAY;
        }
      };
    }

    @Override
    protected TabLabel createTabLabel(TabInfo info) {
      return new MyTabLabel(this, info);
    }
  }

  private static class MyTabLabel extends TabLabel {
    public MyTabLabel(JBTabsImpl tabs, TabInfo info) {
      super(tabs, info);
      final JComponent label = getLabelComponent();
      if (label instanceof SimpleColoredComponent) {
        ((SimpleColoredComponent)label).setIconOnTheRight(true);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(ourPreviewSize + 20, ourPreviewSize);
    }
  }

  private class ClosePlotAction extends AnAction {
    private final TabInfo myInfo;

    public ClosePlotAction(TabInfo info) {
      super("Close Plot", "Close selected plot", AllIcons.Actions.Close);
      myInfo = info;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myTabs.removeTab(myInfo);
    }
  }

  private class SaveAsFileAction extends AnAction {
    public SaveAsFileAction() {
      super("Save as File");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      TabInfo info = myTabs.getSelectedInfo();
      final Project project = e.getProject();
      if (info != null && project != null) {
        final MyPlotPanel component = (MyPlotPanel)info.getComponent();
        final BinaryLightVirtualFile virtualFile = component.getVirtualFile();

        FileSaverDescriptor descriptor = new FileSaverDescriptor("Select File to Save Plot", "", PLOT_FORMAT);
        final FileSaverDialog chooser = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project);

        final VirtualFileWrapper fileWrapper = chooser.save(project.getBaseDir(), PLOT_DEFAULT_NAME);
        try {
          if (fileWrapper != null) {
            final File ioFile = fileWrapper.getFile();
            Files.write(Paths.get(ioFile.getPath()), virtualFile.getContent());
          }
        }
        catch (IOException e1) {
          LOG.warn("Failed to save image " + e1.getMessage());
        }
      }
    }
  }

  private class MyPlotPanel extends JPanel {
    private final PyPlotVirtualFile myVirtualFile;

    public MyPlotPanel(PyPlotVirtualFile virtualFile) {
      super(new BorderLayout());
      myVirtualFile = virtualFile;
      ApplicationManager.getApplication().invokeLater(() -> {
        //ImageFileEditorProvider#EDITOR_TYPE_ID
        final FileEditorProvider provider = FileEditorProviderManager.getInstance().getProvider("images");
        if (provider != null) {
          final FileEditor editor = provider.createEditor(myProject, myVirtualFile);
          Disposer.register(myProject, editor);
          add(editor.getComponent());
        }
      });

      setBackground(UIUtil.getEditorPaneBackground());
    }

    public BinaryLightVirtualFile getVirtualFile() {
      return myVirtualFile;
    }
  }

  class MyDragOutDelegate implements TabInfo.DragOutDelegate {
    private DragSession mySession;

    @Override
    public void dragOutStarted(MouseEvent mouseEvent, TabInfo info) {
      final TabInfo previousSelection = info.getPreviousSelection();
      final Image img = JBTabsImpl.getComponentImage(info);
      info.setHidden(true);
      if (previousSelection != null) {
        myTabs.select(previousSelection, true);
      }

      Presentation presentation = new Presentation(info.getText());
      presentation.setIcon(info.getIcon());
      final MyPlotPanel component = (MyPlotPanel)info.getComponent();
      final BinaryLightVirtualFile file = component.getVirtualFile();
      mySession = getDockManager().createDragSession(mouseEvent, new EditorTabbedContainer.DockableEditor(myProject, img, file, presentation,
                                                                        info.getComponent().getPreferredSize(), false));

    }

    private DockManager getDockManager() {
      return DockManager.getInstance(myProject);
    }

    @Override
    public void processDragOut(MouseEvent event, TabInfo source) {
      mySession.process(event);
    }

    @Override
    public void dragOutFinished(MouseEvent event, TabInfo source) {
      myTabs.removeTab(source);
      mySession.process(event);
      mySession = null;
    }

    @Override
    public void dragOutCancelled(TabInfo source) {
      source.setHidden(false);
      if (mySession != null) {
        mySession.cancel();
      }
      mySession = null;
    }
  }

  public class MyDockContainer implements DockContainer {
    private final ToolWindow myToolWindow;

    public MyDockContainer(ToolWindow toolWindow) {
      myToolWindow = toolWindow;
    }

    @Override
    public RelativeRectangle getAcceptArea() {
      return new RelativeRectangle(myToolWindow.getComponent());
    }

    @Override
    public RelativeRectangle getAcceptAreaFallback() {
      return getAcceptArea();
    }

    @NotNull
    @Override
    public ContentResponse getContentResponse(@NotNull DockableContent content, RelativePoint point) {
      return content.getKey() instanceof PyPlotVirtualFile ? ContentResponse.ACCEPT_MOVE : ContentResponse.DENY;
    }

    @Override
    public JComponent getContainerComponent() {
      return myToolWindow.getComponent();
    }

    @Override
    public void add(@NotNull DockableContent content, RelativePoint dropTarget) {
      final Object key = content.getKey();
      if (key instanceof PyPlotVirtualFile) {
        final TabInfo info = createTabInfo((PyPlotVirtualFile)key);
        myTabs.addTab(info);
      }
    }

    @Override
    public void closeAll() {}

    @Override
    public void addListener(Listener listener, Disposable parent) {}

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Nullable
    @Override
    public Image startDropOver(@NotNull DockableContent content, RelativePoint point) {
      return null;
    }

    @Nullable
    @Override
    public Image processDropOver(@NotNull DockableContent content, RelativePoint point) {
      return null;
    }

    @Override
    public void resetDropOver(@NotNull DockableContent content) {}

    @Override
    public boolean isDisposeWhenEmpty() {
      return false;
    }

    @Override
    public void dispose() {}

    @Override
    public void showNotify() {}

    @Override
    public void hideNotify() {}
  }
}
