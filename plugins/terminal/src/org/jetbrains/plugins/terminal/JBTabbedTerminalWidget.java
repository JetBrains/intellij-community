package org.jetbrains.plugins.terminal;

import com.google.common.base.Predicate;
import com.intellij.ide.dnd.DnDDropHandler;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDSupport;
import com.intellij.ide.dnd.TransferableWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.impl.EditorTabbedContainer;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.DragSession;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.util.ui.UIUtil;
import com.jediterm.terminal.ui.*;
import com.jediterm.terminal.ui.settings.TabbedSettingsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.vfs.TerminalSessionVirtualFileImpl;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author traff
 */
public class JBTabbedTerminalWidget extends TabbedTerminalWidget implements Disposable {

  private Project myProject;
  private final JBTerminalSystemSettingsProvider mySettingsProvider;
  private Disposable myParent;

  public JBTabbedTerminalWidget(@NotNull Project project,
                                @NotNull JBTerminalSystemSettingsProvider settingsProvider,
                                final @NotNull Predicate<Pair<TerminalWidget, String>> createNewSessionAction, @NotNull Disposable parent) {
    super(settingsProvider, new Predicate<TerminalWidget>() {
      @Override
      public boolean apply(TerminalWidget input) {
        return createNewSessionAction.apply(Pair.<TerminalWidget, String>create(input, null));
      }
    });
    myProject = project;

    mySettingsProvider = settingsProvider;
    myParent = parent;

    convertActions(this, getActions());

    Disposer.register(parent, this);
    Disposer.register(this, settingsProvider);

    DnDSupport.createBuilder(this).setDropHandler(new DnDDropHandler() {
      @Override
      public void drop(DnDEvent event) {
        if (event.getAttachedObject() instanceof TransferableWrapper) {
          TransferableWrapper ao = (TransferableWrapper)event.getAttachedObject();
          if (ao != null &&
              ao.getPsiElements() != null &&
              ao.getPsiElements().length == 1 &&
              ao.getPsiElements()[0] instanceof PsiFileSystemItem) {
            PsiFileSystemItem element = (PsiFileSystemItem)ao.getPsiElements()[0];
            PsiDirectory dir = element instanceof PsiFile ? ((PsiFile)element).getContainingDirectory() : (PsiDirectory)element;

            createNewSessionAction.apply(Pair.<TerminalWidget, String>create(JBTabbedTerminalWidget.this, dir.getVirtualFile().getPath()));
          }
        }
      }
    }

    ).install();
  }

  public static void convertActions(@NotNull JComponent component,
                                    @NotNull List<TerminalAction> actions) {
    convertActions(component, actions, null);
  }

  public static void convertActions(@NotNull JComponent component,
                                    @NotNull List<TerminalAction> actions,
                                    @Nullable final Predicate<KeyEvent> elseAction) {
    for (final TerminalAction action : actions) {
      AnAction a = new DumbAwareAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          KeyEvent event = e.getInputEvent() instanceof KeyEvent ? (KeyEvent)e.getInputEvent() : null;
          if (!action.perform(event)) {
            if (elseAction != null) {
              elseAction.apply(event);
            }
          }
        }
      };
      a.registerCustomShortcutSet(action.getKeyCode(), action.getModifiers(), component);
    }
  }

  @Override
  protected JediTermWidget createInnerTerminalWidget(TabbedSettingsProvider settingsProvider) {
    return new JBTerminalWidget(mySettingsProvider, myParent);
  }

  @Override
  protected TerminalTabs createTabbedPane() {
    return new JBTerminalTabs(myProject, myParent);
  }

  public class JBTerminalTabs implements TerminalTabs {
    private final JBEditorTabs myTabs;

    private TabInfo.DragOutDelegate myDragDelegate = new MyDragOutDelegate();

    private final CopyOnWriteArraySet<TabChangeListener> myListeners = new CopyOnWriteArraySet<>();

    public JBTerminalTabs(@NotNull Project project, @NotNull Disposable parent) {
      final ActionManager actionManager = ActionManager.getInstance();
      myTabs = new JBEditorTabs(project, actionManager, IdeFocusManager.getInstance(project), parent) {
        @Override
        protected TabLabel createTabLabel(TabInfo info) {
          return new TerminalTabLabel(this, info);
        }
      };

      myTabs.addListener(new TabsListener.Adapter() {
        @Override
        public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
          for (TabChangeListener each : myListeners) {
            each.selectionChanged();
          }
        }

        @Override
        public void tabRemoved(TabInfo tabInfo) {
          for (TabChangeListener each : myListeners) {
            each.tabRemoved();
          }
        }
      });

      myTabs.setTabDraggingEnabled(true);
    }

    @Override
    public int getSelectedIndex() {
      return myTabs.getIndexOf(myTabs.getSelectedInfo());
    }

    @Override
    public void setSelectedIndex(int index) {
      myTabs.select(myTabs.getTabAt(index), true);
    }

    @Override
    public void setTabComponentAt(int index, Component component) {
      //nop
    }

    @Override
    public int indexOfComponent(Component component) {
      for (int i = 0; i<myTabs.getTabCount(); i++) {
        if (component.equals(myTabs.getTabAt(i).getComponent())) {
          return i;
        }
      }
      
      return -1;
    }

    @Override
    public int indexOfTabComponent(Component component) {
      return 0; //nop
    }


    private TabInfo getTabAt(int index) {
      checkIndex(index);
      return myTabs.getTabAt(index);
    }

    private void checkIndex(int index) {
      if (index < 0 || index >= getTabCount()) {
        throw new ArrayIndexOutOfBoundsException("tabCount=" + getTabCount() + " index=" + index);
      }
    }


    @Override
    public JediTermWidget getComponentAt(int i) {
      return (JediTermWidget)getTabAt(i).getComponent();
    }

    @Override
    public void addChangeListener(TabChangeListener listener) {
      myListeners.add(listener);
    }

    @Override
    public void setTitleAt(int index, String title) {
      getTabAt(index).setText(title);
    }

    @Override
    public void setSelectedComponent(JediTermWidget terminal) {
      TabInfo info = myTabs.findInfo(terminal);
      if (info != null) {
        myTabs.select(info, true);
      }
    }

    @Override
    public JComponent getComponent() {
      return myTabs.getComponent();
    }

    @Override
    public int getTabCount() {
      return myTabs.getTabCount();
    }

    @Override
    public void addTab(String name, JediTermWidget terminal) {
      myTabs.addTab(createTabInfo(name, terminal));
    }

    private TabInfo createTabInfo(String name, JediTermWidget terminal) {
      TabInfo tabInfo = new TabInfo(terminal).setText(name).setDragOutDelegate(myDragDelegate);
      return tabInfo
        .setObject(new TerminalSessionVirtualFileImpl(tabInfo, terminal, mySettingsProvider));
    }

    public String getTitleAt(int i) {
      return getTabAt(i).getText();
    }

    public void removeAll() {
      myTabs.removeAllTabs();
    }

    @Override
    public void remove(JediTermWidget terminal) {
      TabInfo info = myTabs.findInfo(terminal);
      if (info != null) {
        myTabs.removeTab(info);
      }
    }

    private class TerminalTabLabel extends TabLabel {
      public TerminalTabLabel(final JBTabsImpl tabs, final TabInfo info) {
        super(tabs, info);

        setOpaque(false);

        setFocusable(false);

        SimpleColoredComponent label = myLabel;

        //add more space between the label and the button
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

        label.addMouseListener(new MouseAdapter() {

          @Override
          public void mouseReleased(MouseEvent event) {
            handleMouse(event);
          }

          @Override
          public void mousePressed(MouseEvent event) {
            handleMouse(event);
          }

          private void handleMouse(MouseEvent e) {
            if (e.isPopupTrigger()) {
              JPopupMenu menu = createPopup();
              menu.show(e.getComponent(), e.getX(), e.getY());
            }
            else if (e.getButton() != MouseEvent.BUTTON2) {
              myTabs.select(getInfo(), true);

              if (e.getClickCount() == 2 && !e.isConsumed()) {
                e.consume();
                renameTab();
              }
            }
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON2) {
              if (myTabs.getSelectedInfo() == info) {
                closeCurrentSession();
              }
              else {
                myTabs.select(info, true);
              }
            }
          }
        });
      }

      protected JPopupMenu createPopup() {
        JPopupMenu popupMenu = new JPopupMenu();

        TerminalAction.addToMenu(popupMenu, JBTabbedTerminalWidget.this);

        JMenuItem rename = new JMenuItem("Rename Tab");

        rename.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent actionEvent) {
            renameTab();
          }
        });

        popupMenu.add(rename);

        return popupMenu;
      }

      private void renameTab() {
        new TabRenamer() {
          @Override
          protected JTextField createTextField() {
            JBTextField textField = new JBTextField() {
              private int myMinimalWidth;

              @Override
              public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                if (size.width > myMinimalWidth) {
                  myMinimalWidth = size.width;
                }

                return wider(size, myMinimalWidth);
              }

              private Dimension wider(Dimension size, int minimalWidth) {
                return new Dimension(minimalWidth + 10, size.height);
              }
            };
            if (myTabs.useSmallLabels()) {
              textField.setFont(com.intellij.util.ui.UIUtil.getFont(UIUtil.FontSize.SMALL, textField.getFont()));
            }
            textField.setOpaque(true);
            return textField;
          }
        }.install(getSelectedIndex(), getInfo().getText(), myLabel, new TabRenamer.RenameCallBack() {
          @Override
          public void setComponent(Component c) {
            myTabs.setTabDraggingEnabled(!(c instanceof JBTextField));

            setPlaceholderContent(true, (JComponent)c);
          }

          @Override
          public void setNewName(int index, String name) {
            setTitleAt(index, name);
          }
        });
      }
    }

    class MyDragOutDelegate implements TabInfo.DragOutDelegate {

      private TerminalSessionVirtualFileImpl myFile;
      private DragSession mySession;

      @Override
      public void dragOutStarted(MouseEvent mouseEvent, TabInfo info) {
        final TabInfo previousSelection = info.getPreviousSelection();
        final Image img = JBTabsImpl.getComponentImage(info);
        info.setHidden(true);
        if (previousSelection != null) {
          myTabs.select(previousSelection, true);
        }

        myFile = (TerminalSessionVirtualFileImpl)info.getObject();
        Presentation presentation = new Presentation(info.getText());
        presentation.setIcon(info.getIcon());
        mySession = getDockManager()
          .createDragSession(mouseEvent, new EditorTabbedContainer.DockableEditor(myProject, img, myFile, presentation,
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
        myFile.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, Boolean.TRUE);


        myTabs.removeTab(source);

        mySession.process(event);

        myFile.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, null);


        myFile = null;
        mySession = null;
      }

      @Override
      public void dragOutCancelled(TabInfo source) {
        source.setHidden(false);
        if (mySession != null) {
          mySession.cancel();
        }

        myFile = null;
        mySession = null;
      }
    }
  }
}
