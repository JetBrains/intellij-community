/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.ide.navigationToolbar;

import com.intellij.ProjectTopics;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.CopyPasteManagerEx;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeView;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.actionSystem.ex.TimerListener;
import com.intellij.openapi.actionSystem.impl.WeakTimerListener;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.pom.Navigatable;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.JBPopupImpl;
import com.intellij.ui.popup.PopupOwner;
import com.intellij.ui.popup.list.DottedBorder;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * User: anna
 * Date: 03-Nov-2005
 */
public class NavBarPanel extends JPanel implements DataProvider, PopupOwner {
  private static final Icon LEFT_ICON = IconLoader.getIcon("/general/splitLeft.png");
  private static final Icon RIGHT_ICON = IconLoader.getIcon("/general/splitRight.png");

  private ArrayList<MyCompositeLabel> myList = new ArrayList<MyCompositeLabel>();

  private int myFirstIndex = 0;

  private JButton myLeftButton = new JButton(LEFT_ICON);
  private JButton myRightButton = new JButton(RIGHT_ICON);
  private JPanel myScrollablePanel = new JPanel(new GridBagLayout());
  private int myPreferredWidth;
  private NavBarModel myModel;
  protected Project myProject;
  private MyPsiTreeChangeAdapter myPsiTreeChangeAdapter = new MyPsiTreeChangeAdapter();
  private MyProblemListener myProblemListener = new MyProblemListener();
  private MyFileStatusListener myFileStatusListener = new MyFileStatusListener();
  private final MyTimerListener myTimerListener = new MyTimerListener();
  private ModuleDeleteProvider myDeleteModuleProvider = new ModuleDeleteProvider();
  private IdeView myIdeView = new MyIdeView();
  private CopyPasteManagerEx.CopyPasteDelegator myCopyPasteDelegator =
    new CopyPasteManagerEx.CopyPasteDelegator(myProject, NavBarPanel.this) {
      @Nullable
      protected PsiElement[] getSelectedElements() {
        final PsiElement element = getSelectedElement(PsiElement.class);
        return element == null ? null : new PsiElement[]{element};
      }
    };
  private LightweightHint myHint = null;
  private ListPopupImpl myNodePopup = null;
  private Alarm myUpdateAlarm = new Alarm();
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.navigationToolbar.NavigationToolbarPanel");
  private MessageBusConnection myConnection;

  public NavBarPanel(final Project project) {
    myProject = project;
    myModel = new NavBarModel(myProject);
    setLayout(new BorderLayout());
    setBackground(UIUtil.getListBackground());
    setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));

    myScrollablePanel.setBackground(UIUtil.getListBackground());
    myScrollablePanel.setBorder(BorderFactory.createEtchedBorder());

    add(myScrollablePanel, BorderLayout.CENTER);
    add(myLeftButton, BorderLayout.WEST);
    add(myRightButton, BorderLayout.EAST);

    myLeftButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        selectLast();
        shiftFocus(-1);
      }
    });
    myLeftButton.setBorder(null);

    myRightButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        selectLast();
        shiftFocus(1);
      }
    });
    myRightButton.setBorder(null);

    PopupHandler.installPopupHandler(this, IdeActions.GROUP_PROJECT_VIEW_POPUP, ActionPlaces.NAVIGATION_BAR);

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        shiftFocus(-1);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), JComponent.WHEN_FOCUSED);

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        shiftFocus(1);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), JComponent.WHEN_FOCUSED);


    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        shiftFocus(-myModel.getSelectedIndex());
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), JComponent.WHEN_FOCUSED);

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        shiftFocus(myModel.size() - 1 - myModel.getSelectedIndex());
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), JComponent.WHEN_FOCUSED);


    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myModel.getSelectedIndex() != -1) {
          ctrlClick(myModel.getSelectedIndex());
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), JComponent.WHEN_FOCUSED);

    final ActionListener dblClickAction = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myModel.getSelectedIndex() != -1) {
          doubleClick(myModel.getSelectedIndex());
        }
      }
    };

    registerKeyboardAction(dblClickAction, KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), JComponent.WHEN_FOCUSED);

    registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        clearBorder();
        final int selectedIndex = -1;
        myModel.setSelectedIndex(selectedIndex);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);

    registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        final Object o = myModel.getSelectedValue();
        if (myModel.hasChildren(o)) {
          navigateInsideBar(o);
        }
        else {
          doubleClick(myModel.getSelectedIndex());
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);

    registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        ToolWindowManager.getInstance(project).activateEditorComponent();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);

    addFocusListener(new FocusListener() {
      public void focusGained(final FocusEvent e) {}

      public void focusLost(final FocusEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (myProject.isDisposed()) {
              hideHint();
              return;
            }
            final Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(myProject);
            if (focusedComponent != NavBarPanel.this && !isAncestorOf(focusedComponent)) {
              hideHint();
            }
          }
        });

      }
    });

    updateList();
  }

  private void selectLast() {
    if (myModel.getSelectedIndex() == -1 && !myModel.isEmpty()) {
      myModel.setSelectedIndex(myModel.size() - 1);
      paintBorder();
      myList.get(myModel.getSelectedIndex()).requestFocusInWindow();
    }
  }

  public void select() {
    if (!myList.isEmpty()) {
      clearBorder();
      myModel.setSelectedIndex(myList.size() - 1);
      paintComponent();
      scrollSelectionToVisible(1);
      paintBorder();
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          requestFocusInWindow();
        }
      });
    }
  }

  protected void shiftFocus(int direction) {
    clearBorder();
    myModel.setSelectedIndex(myModel.getIndexByMode(myModel.getSelectedIndex() + direction));
    paintBorder();
    scrollSelectionToVisible(direction);
  }

  private void scrollSelectionToVisible(final int direction) {
    final int firstIndex = myFirstIndex;
    while (!myList.get(myModel.getSelectedIndex()).isShowing()) {
      myFirstIndex = myModel.getIndexByMode(myFirstIndex + direction);
      paintComponent();
      if (firstIndex == myFirstIndex) break; //to be sure not to hang
    }
    setSize(getPreferredSize());  //not to miss right button && font corrections
  }

  @Nullable
  public MyCompositeLabel getItem(int index) {
    if (index != -1 && index < myList.size()) {
      return myList.get(index);
    }
    return null;
  }

  /**
   * to be invoked by alarm
   */
  protected void updateList() {
    final DataManagerImpl dataManager = (DataManagerImpl)DataManager.getInstance();
    final Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(myProject);
    if (focusedComponent == null || focusedComponent == this || isAncestorOf(focusedComponent)) {
      immediateUpdateList(false);
    }
    else {
      final DataContext dataContext = dataManager.getDataContextTest(this);
      immediateUpdateList(myModel.updateModel(dataContext));
    }
  }

  protected void immediateUpdateList(boolean update) {
    if (update) {
      myFirstIndex = 0;
      final int selectedIndex1 = -1;
      myModel.setSelectedIndex(selectedIndex1);
      myList.clear();
      for (int index = 0; index < myModel.size(); index++) {
        final Object object = myModel.get(index);
        final MyCompositeLabel label = new MyCompositeLabel();
        label.getLabel().addMouseListener(new MouseAdapter() {
          public void mouseExited(MouseEvent e) {
            if (!myModel.hasChildren(object)) return;
            label.getLabel().setIcon(wrapIcon(object, Color.gray));
            label.repaint();
          }

          public void mouseClicked(MouseEvent e) {
            if (!myModel.hasChildren(object)) return;
            final int selectedIndex = myModel.indexOf(object);
            if (myModel.getSelectedIndex() == selectedIndex && myNodePopup != null) {
              cancelPopup();
              if (isInsideIcon(e.getPoint(), object)) {
                label.getLabel().setIcon(wrapIcon(object, Color.black));
                label.getLabel().repaint();
              }
              return;
            }
            if (isInsideIcon(e.getPoint(), object)) {
              ctrlClick(selectedIndex);
              clearBorder();
              myModel.setSelectedIndex(selectedIndex);
              paintBorder();
              label.getLabel().setIcon(wrapIcon(object, Color.black));
              label.getLabel().repaint();
            }
          }
        });
        label.getLabel().addMouseMotionListener(new MouseMotionAdapter() {
          public void mouseMoved(MouseEvent e) {
            if (!myModel.hasChildren(object)) return;
            if (isInsideIcon(e.getPoint(), object)) {
              label.getLabel().setIcon(wrapIcon(object, Color.black));
            }
            else {
              label.getLabel().setIcon(wrapIcon(object, Color.gray));
            }
            label.repaint();
          }
        });
        label.setFont(UIUtil.getLabelFont());
        label.getLabel().setIcon(wrapIcon(object, Color.gray));
        label.getColoredComponent().append(myModel.getPresentableText(object, getWindow()), myModel.getTextAttributes(object, false));
        clearBorder(label.getColoredComponent());
        label.getLabel().setOpaque(false);
        label.getColoredComponent().setOpaque(true);
        label.setBackground(UIUtil.getListBackground());
        myList.add(label);
        installActions(index);
      }
      paintComponent();
    }
  }

  private Icon wrapIcon(final Object object, final Color color) {
    final Icon icon = NavBarModel.getIcon(object);
    if (icon == null || !myModel.hasChildren(object)) return icon;
    LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(icon, 0);
    Icon plusIcon = new Icon() {
      public void paintIcon(Component c, Graphics g, int x, int y) {
        g.setColor(color);
        g.drawRect(x + 1, y - 4, 8, 8);
        g.drawLine(x + 3, y, x + 7, y);
        if (myModel.getSelectedIndex() != myModel.indexOf(object) || myNodePopup == null || !myNodePopup.isVisible()) {
          g.drawLine(x + 5, y - 2, x + 5, y + 2);
        }
      }

      public int getIconWidth() {
        return 10;
      }

      public int getIconHeight() {
        return 8;
      }
    };
    layeredIcon.setIcon(plusIcon, 1, -12, icon.getIconHeight() / 2);
    return layeredIcon;
  }

  private static boolean isInsideIcon(final Point point, final Object object) {
    //noinspection ConstantConditions
    final int height = NavBarModel.getIcon(object).getIconHeight();
    return point.x > 0 && point.x < 10 && point.y > height / 2 - 4 && point.y < height / 2 + 4;
  }

  private void paintComponent() {
    myPreferredWidth = 0;
    myScrollablePanel.removeAll();
    myScrollablePanel.revalidate();
    final GridBagConstraints gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 1, 1, 1, 0, 1, GridBagConstraints.WEST,
                                                         GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    final MyCompositeLabel toBeContLabel = getDotsLabel();
    clearBorder(toBeContLabel.getColoredComponent());
    final int additionalWidth = toBeContLabel.getPreferredSize().width;
    final Window window = SwingUtilities.getWindowAncestor(this);
    final int availableWidth = window != null ? window.getWidth() - 2 * LEFT_ICON.getIconWidth() - 2 * additionalWidth : 0;
    int lastIndx = -1;
    if (myModel.getSelectedIndex() != -1) {
      myScrollablePanel.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
      if (myFirstIndex > 0) {
        final MyCompositeLabel preList = getDotsLabel();
        clearBorder(preList.getColoredComponent());
        myScrollablePanel.add(preList, gc);
        myPreferredWidth += additionalWidth;
      }
      for (int i = myFirstIndex; i < myList.size(); i++) {
        final MyCompositeLabel linkLabel = myList.get(i);
        final int labelWidth = linkLabel.getPreferredSize().width;
        if (myPreferredWidth + labelWidth < availableWidth) {
          myScrollablePanel.add(linkLabel, gc);
          myPreferredWidth += labelWidth;
        }
        else {
          myScrollablePanel.add(toBeContLabel, gc);
          myPreferredWidth += additionalWidth;
          lastIndx = i;
          break;
        }
      }
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      myScrollablePanel.add(Box.createHorizontalBox(), gc);
    }
    else if (!myModel.isEmpty()) {
      myScrollablePanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      myScrollablePanel.add(Box.createHorizontalBox(), gc);

      gc.weightx = 0;
      gc.fill = GridBagConstraints.NONE;
      final MyCompositeLabel preselected = myList.get(myModel.size() - 1);
      installDottedBorder(preselected.getColoredComponent());
      for (int i = myModel.size() - 1; i >= 0; i--) {
        final MyCompositeLabel linkLabel = myList.get(i);
        final int labelWidth = linkLabel.getPreferredSize().width;
        if (availableWidth == 0 || myPreferredWidth + labelWidth < availableWidth) {
          myScrollablePanel.add(linkLabel, gc);
          myPreferredWidth += labelWidth;
        }
        else {
          myFirstIndex = i + 1;
          myScrollablePanel.add(toBeContLabel, gc);
          myPreferredWidth += additionalWidth;
          break;
        }
      }
    }

    myPreferredWidth += 2 * LEFT_ICON.getIconWidth();
    final boolean hasNavigationButtons = lastIndx > 0 || myFirstIndex > 0;
    myLeftButton.setVisible(hasNavigationButtons);
    myRightButton.setVisible(hasNavigationButtons);
    repaint();
  }

  private static MyCompositeLabel getDotsLabel() {
    final MyCompositeLabel dotsLabel = new MyCompositeLabel();
    dotsLabel.getColoredComponent().setFont(UIUtil.getLabelFont());
    dotsLabel.getColoredComponent().append("...", SimpleTextAttributes.REGULAR_ATTRIBUTES);
    dotsLabel.setBackground(UIUtil.getListBackground());
    return dotsLabel;
  }

  //-------------- borders -----------------------------

  private void paintBorder() {
    final MyCompositeLabel focusedLabel = myList.get(myModel.getSelectedIndex());
    final Object o = myModel.getSelectedValue();
    focusedLabel.getLabel().setIcon(wrapIcon(o, Color.gray));
    final SimpleColoredComponent simpleColoredComponent = focusedLabel.getColoredComponent();
    simpleColoredComponent.clear();
    simpleColoredComponent.append(myModel.getPresentableText(o, getWindow()), myModel.getTextAttributes(o, true));
    simpleColoredComponent.setBackground(UIUtil.getListSelectionBackground());
    simpleColoredComponent.setForeground(UIUtil.getListSelectionForeground());
    installDottedBorder(simpleColoredComponent);
  }

  private static void installDottedBorder(SimpleColoredComponent label) {
    label.setBorder(new DottedBorder(new Insets(0, 2, 0, 4), UIUtil.getListForeground()));
  }

  private void clearBorder() {
    if (myModel.isEmpty()) return;
    final int index = myModel.getSelectedIndex() != -1 ? myModel.getSelectedIndex() : myModel.size() - 1;
    final MyCompositeLabel focusLostLabel = myList.get(index);
    final Object o = myModel.get(index);
    focusLostLabel.getLabel().setIcon(wrapIcon(o, Color.gray));
    final SimpleColoredComponent simpleColoredComponent = focusLostLabel.getColoredComponent();
    simpleColoredComponent.clear();
    simpleColoredComponent.append(myModel.getPresentableText(o, getWindow()), myModel.getTextAttributes(o, false));
    simpleColoredComponent.setBackground(UIUtil.getListBackground());
    simpleColoredComponent.setForeground(UIUtil.getListForeground());
    clearBorder(simpleColoredComponent);
  }

  private static void clearBorder(SimpleColoredComponent label) {
    label.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 4));
  }

  private Window getWindow() {
    return SwingUtilities.getWindowAncestor(this);
  }

  // ------ NavBar actions -------------------------
  private void installActions(final int index) {
    final SimpleColoredComponent label = myList.get(index).getColoredComponent();
    label.addMouseListener(getMouseListener(new Condition<MouseEvent>() {
      public boolean value(final MouseEvent e) {
        return !e.isConsumed() && !e.isPopupTrigger() && e.getClickCount() == 2;
      }
    }, new Runnable() {
      public void run() {
        doubleClick(index);
      }
    }, index));

    label.addMouseListener(getMouseListener(new Condition<MouseEvent>() {
      public boolean value(final MouseEvent e) {
        // You cannot distinguish between 3rd mouse button released with Meta down or not. See SunBug: 4029159
        if (e.getID() != MouseEvent.MOUSE_PRESSED && SystemInfo.isMac) return false;

        final int ex = e.getModifiersEx();
        return !e.isConsumed() && !e.isPopupTrigger() &&
               (ex & (SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK)) != 0;
      }
    }, new Runnable() {
      public void run() {
        ctrlClick(index);
      }
    }, index));

    label.addMouseListener(getMouseListener(new Condition<MouseEvent>() {
      public boolean value(final MouseEvent e) {
        return !e.isConsumed() && e.isPopupTrigger();
      }
    }, new Runnable() {
      public void run() {
        rightClick(index);
      }
    }, index));


    label.addMouseListener(getMouseListener(new Condition<MouseEvent>() {
      public boolean value(final MouseEvent e) {
        return !e.isConsumed() && e.getClickCount() == 1 && !e.isPopupTrigger();
      }
    }, new Runnable() {
      public void run() {
        //just select
        requestFocusInWindow();
        cancelPopup();
      }
    }, index));
  }

  private MouseListener getMouseListener(final Condition<MouseEvent> condition, final Runnable handler, final int index) {
    return new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        onClick(e);
      }

      public void mousePressed(MouseEvent e) {
        onClick(e);
      }

      public void mouseReleased(MouseEvent e) {
        onClick(e);
      }

      private void onClick(MouseEvent e) {
        if (condition.value(e)) {
          clearBorder();
          myModel.setSelectedIndex(index);
          paintBorder();
          myList.get(myModel.getSelectedIndex()).requestFocusInWindow();
          handler.run();
          e.consume();
        }
      }
    };
  }

  private void doubleClick(final int index) {
    Object object = myModel.getElement(index);
    if (object instanceof Navigatable) {
      final Navigatable navigatable = (Navigatable)object;
      if (navigatable.canNavigate()) {
        navigatable.navigate(true);
      }
    }
    else if (object instanceof Module) {
      final ProjectView projectView = ProjectView.getInstance(myProject);
      final AbstractProjectViewPane projectViewPane = projectView.getProjectViewPaneById(projectView.getCurrentViewId());
      projectViewPane.selectModule((Module)object, true);
    }
    else if (object instanceof Project) {
      return;
    }
    hideHint();
  }

  private void ctrlClick(final int index) {
    if (myNodePopup != null && myNodePopup.isVisible() && myModel.getSelectedIndex() == index) {
      cancelPopup();
      return;
    }
    final Object object = myModel.getElement(index);
    final List<Object> objects = myModel.calcElementChildren(object);
    if (!objects.isEmpty()) {
      Object[] siblings = new Object[objects.size()];
      Icon[] icons = new Icon[objects.size()];
      for (int i = 0; i < objects.size(); i++) {
        siblings[i] = objects.get(i);
        if (siblings[i] instanceof Module) {
          icons[i] = ((Module)siblings[i]).getModuleType().getNodeIcon(false);
        }
        else if (siblings[i] instanceof PsiElement) {
          icons[i] = ((PsiElement)siblings[i]).getIcon(Iconable.ICON_FLAG_OPEN);
        }
      }
      final BaseListPopupStep<Object> step = new BaseListPopupStep<Object>("", siblings, icons) {
        public boolean isSpeedSearchEnabled() { return true; }
        public boolean isSelectable(Object value) { return true; }
        public void canceled() {
          super.canceled();
          repaint(); //update +
        }
        @NotNull
        public String getTextFor(Object value) {
          final String presentableText = myModel.getPresentableText(value, getWindow());
          return presentableText != null ? presentableText : "";
        }
        public PopupStep onChosen(final Object selectedValue, final boolean finalChoice) {
          if (selectedValue instanceof PsiFile) {
            final Navigatable navigatable = (Navigatable)selectedValue;
            if (navigatable.canNavigate()) {
              navigatable.navigate(true);
            }
            hideHint();
          }
          else {
            navigateInsideBar(selectedValue);
          }
          return PopupStep.FINAL_CHOICE;
        }
      };
      step.setDefaultOptionIndex(index < myModel.size() - 1 ? objects.indexOf(myModel.getElement(index + 1)) : 0);
      myNodePopup = new ListPopupImpl(step) {
        protected ListCellRenderer getListElementRenderer() {
          return new MySiblingsListCellRenderer();
        }
      };
      myNodePopup.registerAction("left", KeyEvent.VK_LEFT, 0, new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          myNodePopup.goBack();
          shiftFocus(-1);
          click();
        }
      });
      myNodePopup.registerAction("right", KeyEvent.VK_RIGHT, 0, new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          myNodePopup.goBack();
          shiftFocus(1);
          click();
        }
      });
      myNodePopup.showUnderneathOf(getItem(index).getColoredComponent());
    }
    repaint();
  }

  private void navigateInsideBar(Object object) {
    if (object instanceof PsiElement) {
      final Object rootElement = myModel.size() > 1 ? myModel.getElement(1) : null;
      if (rootElement instanceof Module) {
        final Module module = (Module)rootElement;
        myModel.removeAllElements();
        myModel.addElement(module.getProject());
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        myModel.addElement(module);
        myModel.traverseToRoot((PsiElement)object, new HashSet<VirtualFile>(Arrays.asList(moduleRootManager.getContentRoots())));
      }
      else {
        myModel.updateModel((PsiElement)object);
      }
    }
    else if (object instanceof Module) {
      myModel.removeAllElements();
      myModel.addElement(((Module)object).getProject());
      myModel.addElement(object);
    }
    immediateUpdateList(true);
    select();

    if (myHint != null) {
      immediateUpdateList(false); //to set up preffered size
      final Dimension dimension = getPreferredSize();
      final Rectangle bounds = myHint.getBounds();
      myHint.setBounds(bounds.x, bounds.y, dimension.width, dimension.height);
      select(); //restore selection
    }

    validate(); //calc bounds
    click();
  }

  private void rightClick(final int index) {
    final ActionManager actionManager = ActionManager.getInstance();
    final ActionGroup group = (ActionGroup)actionManager.getAction(IdeActions.GROUP_PROJECT_VIEW_POPUP);
    final ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(ActionPlaces.NAVIGATION_BAR, group);
    final MyCompositeLabel item = getItem(index);
    if (item != null) {
      popupMenu.getComponent().show(this, item.getX(), item.getY() + item.getHeight());
    }
  }

  private void click() {
    cancelPopup();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        ctrlClick(myModel.getSelectedIndex());
      }
    });
  }

  private void cancelPopup() {
    if (myNodePopup != null) {
      myNodePopup.cancel();
      myNodePopup = null;
    }
  }

  private void hideHint() {
    if (myHint != null) {
      myHint.hide();
      myHint = null;
    }
  }

  public Dimension getPreferredSize() {
    return new JButton("1").getPreferredSize();
  }

  protected int getPreferredWidth() {
    return myPreferredWidth + 2 * LEFT_ICON.getIconWidth();
  }

  @Nullable
  public Object getData(String dataId) {
    if (dataId.equals(DataConstants.PROJECT)) {
      return myProject;
    }
    if (dataId.equals(DataConstants.MODULE)) {
      final Module module = getSelectedElement(Module.class);
      if (module != null && !module.isDisposed()) return module;
      final PsiElement element = getSelectedElement(PsiElement.class);
      if (element != null) {
        return ModuleUtil.findModuleForPsiElement(element);
      }
      return null;
    }
    if (dataId.equals(DataConstants.MODULE_CONTEXT)) {
      final Module module = getSelectedElement(Module.class);
      return module != null && module.isDisposed() ? null : module;
    }
    if (dataId.equals(DataConstants.PSI_ELEMENT)) {
      final PsiElement element = getSelectedElement(PsiElement.class);
      return element != null && element.isValid() ? element : null;
    }
    if (dataId.equals(DataConstants.CONTEXT_COMPONENT)) {
      return this;
    }
    if (DataConstantsEx.CUT_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator.getCutProvider();
    }
    if (DataConstantsEx.COPY_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator.getCopyProvider();
    }
    if (DataConstantsEx.PASTE_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator.getPasteProvider();
    }
    if (DataConstantsEx.DELETE_ELEMENT_PROVIDER.equals(dataId)) {
      return getSelectedElement(Module.class) != null ? myDeleteModuleProvider : new DeleteHandler.DefaultDeleteProvider();
    }

    if (DataConstants.IDE_VIEW.equals(dataId)) {
      return myIdeView;
    }

    return null;
  }

  @Nullable
  @SuppressWarnings({"unchecked"})
  private <T> T getSelectedElement(Class<T> klass) {
    Object selectedValue1 = myModel.getSelectedValue();
    if (selectedValue1 == null) {
      final int modelSize = myModel.size();
      if (modelSize > 0) {
        selectedValue1 = myModel.getElement(modelSize - 1);
      }
    }
    final Object selectedValue = selectedValue1;
    return selectedValue != null && klass.isAssignableFrom(selectedValue.getClass()) ? (T)selectedValue : null;
  }

  public Point getBestPopupPosition() {
    int index = myModel.getSelectedIndex();
    final int modelSize = myModel.size();
    if (index == -1) {
      index = modelSize - 1;
    }
    if (index > -1 && index < modelSize) {
      final MyCompositeLabel item = getItem(index);
      if (item != null) {
        return new Point(item.getX(), item.getY() + item.getHeight());
      }
    }
    return null;
  }

  // ----- inplace NavBar -----------
  public void installListeners() {
    myConnection = myProject.getMessageBus().connect();

    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeAdapter);
    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyModuleRootListener());
    WolfTheProblemSolver.getInstance(myProject).addProblemListener(myProblemListener);
    FileStatusManager.getInstance(myProject).addFileStatusListener(myFileStatusListener);
  }

  public void uninstallListeners() {
    if (myConnection != null) {
      myConnection.disconnect();
    }

    WolfTheProblemSolver.getInstance(myProject).removeProblemListener(myProblemListener);
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeAdapter);
    FileStatusManager.getInstance(myProject).removeFileStatusListener(myFileStatusListener);
    myUpdateAlarm.cancelAllRequests();
  }

  public void addNotify() {
    super.addNotify();
    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    actionManager.addTimerListener(500, new WeakTimerListener(actionManager, myTimerListener));
  }

  public void updateState(final boolean show) {
    final int selectedIndex = myModel.getSelectedIndex();
    if (show && selectedIndex > -1 && selectedIndex < myModel.size()) {
      final MyCompositeLabel item = getItem(selectedIndex);
      if (item != null) {
        item.requestFocusInWindow();
      }
    }
  }

  // ------ popup NavBar ----------
  public void showHint(@Nullable final Editor editor, final DataContext dataContext) {
    updateList();
    if (myModel.size() == 0) return;
    myHint = new LightweightHint(this);
    myHint.setForceLightweightPopup(true);
    registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        hideHint();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);
    final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    final Window focusedWindow = focusManager.getFocusedWindow();
    if (editor == null) {
      final RelativePoint relativePoint = JBPopupFactory.getInstance().guessBestPopupLocation(dataContext);
      final Component owner = focusManager.getFocusOwner();
      final Component cmp = relativePoint.getComponent();
      if (cmp instanceof JComponent) {
        myHint.show((JComponent)cmp, relativePoint.getPoint().x, relativePoint.getPoint().y,
                    owner instanceof JComponent ? (JComponent)owner : null);
      }
    }
    else {
      final Container container = focusedWindow != null ? focusedWindow : editor.getContentComponent();
      final Point p = JBPopupImpl.getCenterOf(container, this);
      p.y = container.getHeight() / 4;
      HintManager.getInstance().showEditorHint(myHint, editor, p, HintManager.HIDE_BY_ESCAPE, 0, true);
    }
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        select();
      }
    });
  }

  protected static class MyCompositeLabel extends JPanel {

    private JLabel myLabel = new JLabel();

    private SimpleColoredComponent myColoredComponent = new SimpleColoredComponent();

    public MyCompositeLabel() {
      super(new GridBagLayout());
      final GridBagConstraints gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST,
                                                           GridBagConstraints.NONE, new Insets(0, 2, 0, 0), 0, 0);
      add(myLabel, gc);
      gc.insets.left = 1;
      add(myColoredComponent, gc);
    }

    public JLabel getLabel() {
      return myLabel;
    }

    public SimpleColoredComponent getColoredComponent() {
      return myColoredComponent;
    }

  }

  private final class MyTimerListener implements TimerListener {

    public ModalityState getModalityState() {
      return ModalityState.stateForComponent(NavBarPanel.this);
    }

    public void run() {
      if (!isShowing()) {
        return;
      }

      Window mywindow = SwingUtilities.windowForComponent(NavBarPanel.this);
      if (mywindow != null && !mywindow.isActive()) return;


      final MenuSelectionManager menuSelectionManager = MenuSelectionManager.defaultManager();
      final MenuElement[] selectedPath = menuSelectionManager.getSelectedPath();
      if (selectedPath.length > 0) {
        return;
      }

      final Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      if (window instanceof Dialog) {
        final Dialog dialog = (Dialog)window;
        if (dialog.isModal() && !SwingUtilities.isDescendingFrom(NavBarPanel.this, dialog)) {
          return;
        }
      }

      updateList();
    }

  }

  private final class MyIdeView implements IdeView {

    public void selectElement(PsiElement element) {
      myModel.updateModel(element);
      immediateUpdateList(true);
      if (element instanceof Navigatable) {
        final Navigatable navigatable = (Navigatable)element;
        if (navigatable.canNavigate()) {
          ((Navigatable)element).navigate(true);
        }
      }
      hideHint();
    }

    public PsiDirectory[] getDirectories() {
      final PsiDirectory dir = getSelectedElement(PsiDirectory.class);
      if (dir != null && dir.isValid()) {
        return new PsiDirectory[]{dir};
      }
      final PsiElement element = getSelectedElement(PsiElement.class);
      if (element != null && element.isValid()) {
        final PsiFile file = element.getContainingFile();
        if (file != null) {
          return new PsiDirectory[]{file.getContainingDirectory()};
        }
      }
      final PsiPackage psiPackage = getSelectedElement(PsiPackage.class);
      if (psiPackage != null && psiPackage.isValid()) {
        return psiPackage.getDirectories();
      }
      final Module module = getSelectedElement(Module.class);
      if (module != null && !module.isDisposed()) {
        ArrayList<PsiDirectory> dirs = new ArrayList<PsiDirectory>();
        final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
        final PsiManager psiManager = PsiManager.getInstance(myProject);
        for (VirtualFile virtualFile : sourceRoots) {
          final PsiDirectory directory = psiManager.findDirectory(virtualFile);
          if (directory != null && directory.isValid()) {
            dirs.add(directory);
          }
        }
        return dirs.toArray(new PsiDirectory[dirs.size()]);
      }
      return PsiDirectory.EMPTY_ARRAY;
    }

    public PsiDirectory getOrChooseDirectory() {
      return PackageUtil.getOrChooseDirectory(this);
    }

  }

  private void updateListLater() {
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(new Runnable() {
      public void run() {
        immediateUpdateList(true);
        paintComponent();
      }
    }, 500, ModalityState.NON_MODAL);
  }

  private class MyPsiTreeChangeAdapter extends PsiTreeChangeAdapter {
    public void childAdded(PsiTreeChangeEvent event) {
      updateListLater();
    }

    public void beforeChildRemoval(final PsiTreeChangeEvent event) {
      myUpdateAlarm.cancelAllRequests();
      myUpdateAlarm.addRequest(new Runnable() {
        public void run() {
          immediateUpdateList(myModel.updateModel(event.getParent()));
        }
      }, 500, ModalityState.NON_MODAL);
    }

    public void childReplaced(PsiTreeChangeEvent event) {
      updateListLater();
    }

    public void childMoved(PsiTreeChangeEvent event) {
      updateListLater();
    }

    public void childrenChanged(PsiTreeChangeEvent event) {
      updateListLater();
    }

    public void propertyChanged(final PsiTreeChangeEvent event) {
      final String propertyName = event.getPropertyName();
      if (propertyName.equals(PsiTreeChangeEvent.PROP_FILE_NAME) || propertyName.equals(PsiTreeChangeEvent.PROP_DIRECTORY_NAME)) {
        updateListLater();
      }
    }
  }

  private class MyModuleRootListener implements ModuleRootListener {
    public void beforeRootsChange(ModuleRootEvent event) {
    }

    public void rootsChanged(ModuleRootEvent event) {
      updateListLater();
    }
  }

  private class MyProblemListener extends WolfTheProblemSolver.ProblemListener {

    public void problemsAppeared(VirtualFile file) {
      updateListLater();
    }

    public void problemsDisappeared(VirtualFile file) {
      updateListLater();
    }

  }

  private class MyFileStatusListener implements FileStatusListener {

    public void fileStatusesChanged() {
      updateListLater();
    }

    public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
      updateListLater();
    }
  }

  private class MySiblingsListCellRenderer extends ColoredListCellRenderer {
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      setFocusBorderAroundIcon(false);
      String name = myModel.getPresentableText(value, getWindow());
      LOG.assertTrue(name != null);
      Color color = list.getForeground();
      boolean isProblemFile = false;
      if (value instanceof PsiElement) {
        final PsiElement psiElement = (PsiElement)value;
        PsiFile psiFile = psiElement.getContainingFile();
        if (psiFile != null) {
          VirtualFile vFile = psiFile.getVirtualFile();
          if (vFile != null) {
            if (WolfTheProblemSolver.getInstance(myProject).isProblemFile(vFile)) {
              isProblemFile = true;
            }
            FileStatus status = FileStatusManager.getInstance(myProject).getStatus(vFile);
            color = status.getColor();
          }
        }
        else {
          isProblemFile = WolfTheProblemSolver.getInstance(myProject).hasProblemFilesBeneath(psiElement);
        }
      }
      else if (value instanceof Module) {
        final Module module = (Module)value;
        isProblemFile = WolfTheProblemSolver.getInstance(myProject).hasProblemFilesBeneath(module);
      }
      else if (value instanceof Project) {
        final Module[] modules = ModuleManager.getInstance((Project)value).getModules();
        for (Module module : modules) {
          if (WolfTheProblemSolver.getInstance(myProject).hasProblemFilesBeneath(module)) {
            isProblemFile = true;
            break;
          }
        }
      }
      SimpleTextAttributes nameAttributes;
      if (isProblemFile) {
        TextAttributes attributes = new TextAttributes(color, null, Color.red, EffectType.WAVE_UNDERSCORE, Font.PLAIN);
        nameAttributes = SimpleTextAttributes.fromTextAttributes(attributes);
      }
      else {
        nameAttributes = new SimpleTextAttributes(Font.PLAIN, color);
      }
      append(name, nameAttributes);
      setIcon(NavBarModel.getIcon(value));
      setPaintFocusBorder(false);
      setBackground(selected ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground());
    }
  }
}
