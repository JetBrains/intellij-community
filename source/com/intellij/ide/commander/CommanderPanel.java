package com.intellij.ide.commander;

import com.intellij.ide.CopyPasteManagerEx;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.nodes.Form;
import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.ui.customization.CustomizableActionsSchemas;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.*;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;

/**
 * @author Eugene Belyaev
 */
public class CommanderPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.commander.CommanderPanel");

  private static final Color DARK_BLUE = new Color(55, 85, 134);
  private static final Color DARK_BLUE_BRIGHTER = new Color(58, 92, 149);
  private static final Color DARK_BLUE_DARKER = new Color(38, 64, 106);

  private Project myProject;
  private AbstractListBuilder myBuilder;
  private JPanel myTitlePanel;
  private JLabel myParentTitle;
  protected final JList myList;
  private final DefaultListModel myModel;

  private final Commander myCommander;
  private CopyPasteManagerEx.CopyPasteDelegator myCopyPasteDelegator;
  protected final ListSpeedSearch myListSpeedSearch;
  private final IdeView myIdeView = new MyIdeView();
  private final MyDeleteElementProvider myDeleteElementProvider = new MyDeleteElementProvider();
  @NonNls
  private static final String ACTION_DRILL_DOWN = "DrillDown";
  @NonNls
  private static final String ACTION_GO_UP = "GoUp";
  private ProjectAbstractTreeStructureBase myProjectTreeStructure;

  public CommanderPanel(final Project project, final Commander commander) {
    super(new BorderLayout());
    myProject = project;
    myModel = new DefaultListModel();
    myList = new JList(myModel);
    myList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    myCommander = commander;

    if (commander != null) {
      myCopyPasteDelegator = new CopyPasteManagerEx.CopyPasteDelegator(myProject, myList) {
        protected PsiElement[] getSelectedElements() {
          return CommanderPanel.this.getSelectedElements();
        }
      };
    }

    myListSpeedSearch = new ListSpeedSearch(myList);

    ListScrollingUtil.installActions(myList);

    myList.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          if (myBuilder == null) return;
          myBuilder.buildRoot();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, SystemInfo.isMac ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK),
      JComponent.WHEN_FOCUSED
    );

    myList.getInputMap(JComponent.WHEN_FOCUSED).put(
      KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
      ACTION_DRILL_DOWN
    );
    myList.getInputMap(JComponent.WHEN_FOCUSED).put(
      KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, SystemInfo.isMac ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK),
      ACTION_DRILL_DOWN
    );
    myList.getActionMap().put(
      ACTION_DRILL_DOWN,
      new AbstractAction() {
        public void actionPerformed(final ActionEvent e) {
          drillDown();
        }
      }
    );
    myList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          drillDown();
        }
      }
    });
    myList.getInputMap(JComponent.WHEN_FOCUSED).put(
      KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, SystemInfo.isMac ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK),
      ACTION_GO_UP
    );
    myList.getInputMap(JComponent.WHEN_FOCUSED).put(
      KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0),
      ACTION_GO_UP
    );
    myList.getActionMap().put(
      ACTION_GO_UP,
      new AbstractAction() {
        public void actionPerformed(final ActionEvent e) {
          goUp();
        }
      }
    );

    //noinspection HardCodedStringLiteral
    myList.getActionMap().put(
      "selectAll",
      new AbstractAction() {
        public void actionPerformed(final ActionEvent e) {
        }
      }
    );


    myList.addMouseListener(
      new PopupHandler() {
        public void invokePopup(final Component comp, final int x, final int y) {
          CommanderPanel.this.invokePopup(comp, x, y);
        }
      }
    );

    myList.addKeyListener(
      new KeyAdapter() {
        public void keyPressed(final KeyEvent e) {
          if (KeyEvent.VK_ESCAPE == e.getKeyCode()) {
            if (e.isConsumed()) return;
            final CopyPasteManagerEx copyPasteManager = (CopyPasteManagerEx)CopyPasteManager.getInstance();
            final boolean[] isCopied = new boolean[1];
            if (copyPasteManager.getElements(isCopied) != null && !isCopied[0]) {
              copyPasteManager.clear();
              e.consume();
            }
          }
        }
      }
    );

    myList.addFocusListener(
      new FocusAdapter() {
        public void focusGained(final FocusEvent e) {
          setActive(true);
        }

        public void focusLost(final FocusEvent e) {
          setActive(false);
        }
      }
    );

    ListToolTipHandler.install(myList);
  }

  private void updateHistory(boolean elementExpanded) {
    if (myCommander != null) {
      myCommander.getCommandHistory().saveState(getSelectedElement(), elementExpanded, myCommander.isLeftPanelActive());
    }
  }

  final JList getList() {
    return myList;
  }

  public final DefaultListModel getModel() {
    return myModel;
  }

  public void goUp() {
    if (myBuilder == null) {
      return;
    }
    updateHistory(true);
    myBuilder.goUp();
    updateHistory(false);
  }

  public void drillDown() {
    if (topElementIsSelected()){
      goUp();
      return;
    }

    if (getSelectedValue() == null) {
      return;
    }

    final AbstractTreeNode element = getSelectedNode();
    if (element.getChildren().size() == 0) {
      if (!shouldDrillDownOnEmptyElement(element.getValue())) {
        navigateSelectedElement();
        return;
      }
    }

    if (myBuilder == null) {
      return;
    }
    updateHistory(false);
    myBuilder.drillDown();
    updateHistory(true);
  }

  public boolean navigateSelectedElement() {
    final AbstractTreeNode selectedNode = getSelectedNode();
    if (selectedNode != null) {
      if (selectedNode.canNavigateToSource()) {
        selectedNode.navigate(true);
        return true;
      }
    }
    return false;
  }

  protected boolean shouldDrillDownOnEmptyElement(final Object value) {
    return !(value instanceof PsiMethod || value instanceof PsiField || isForm(value));
  }

  private boolean isForm(final Object value) {
    return value instanceof PsiFile && ((PsiFile)value).getVirtualFile().getFileType() == StdFileTypes.GUI_DESIGNER_FORM;
  }

  private boolean topElementIsSelected() {
    int[] selectedIndices = myList.getSelectedIndices();
    return selectedIndices.length == 1 && selectedIndices[0] == 0 && (myModel.getElementAt(selectedIndices[0]) instanceof TopLevelNode);
  }

  public final void setBuilder(final AbstractListBuilder builder) {
    myBuilder = builder;
    removeAll();

    myTitlePanel = new JPanel(new BorderLayout());
    myTitlePanel.setBackground(UIUtil.getControlColor());
    myTitlePanel.setOpaque(true);

    myParentTitle = new MyTitleLabel(myTitlePanel);
    myParentTitle.setText(" ");
    myParentTitle.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    myParentTitle.setForeground(Color.black);
    myParentTitle.setUI(new RightAlignedLabelUI());
    final JPanel panel1 = new JPanel(new BorderLayout());
    panel1.setOpaque(false);
    panel1.add(Box.createHorizontalStrut(10), BorderLayout.WEST);
    panel1.add(myParentTitle, BorderLayout.CENTER);
    myTitlePanel.add(panel1, BorderLayout.CENTER);

    add(myTitlePanel, BorderLayout.NORTH);
    final JScrollPane scrollPane = new JScrollPane(myList);
    scrollPane.getVerticalScrollBar().setFocusable(false); // otherwise the scrollbar steals focus and panel switching with tab is broken 
    scrollPane.getHorizontalScrollBar().setFocusable(false);
    add(scrollPane, BorderLayout.CENTER);

    myBuilder.setParentTitle(myParentTitle);

    // TODO[vova,anton] it seems that the code below performs double focus request. Is it OK?
    myTitlePanel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        myList.requestFocus();
      }

      public void mousePressed(final MouseEvent e) {
        myList.requestFocus();
      }
    });
  }

  public final AbstractListBuilder getBuilder() {
    return myBuilder;
  }

  public final PsiElement getSelectedElement() {
    Object value = getValueAtIndex(getSelectedNode());
    return (PsiElement)(value instanceof PsiElement ? value : null);
  }

  public final PsiElement getSelectedElement(int index) {
    Object elementAtIndex = myModel.getElementAt(index);
    Object value = getValueAtIndex(elementAtIndex instanceof AbstractTreeNode ? (AbstractTreeNode)elementAtIndex : null);
    return (PsiElement)(value instanceof PsiElement ? value : null);
  }

  private AbstractTreeNode getSelectedNode(){
    if (myBuilder == null) return null;
    final int[] indices = myList.getSelectedIndices();
    if (indices.length != 1) return null;
    int index = indices[0];
    if (index >= myModel.getSize()) return null;
    Object elementAtIndex = myModel.getElementAt(index);
    return elementAtIndex instanceof AbstractTreeNode ? (AbstractTreeNode)elementAtIndex : null;
  }



  public Object getSelectedValue() {
    return getValueAtIndex(getSelectedNode());
  }

  private PsiElement[] getSelectedElements() {
    if (myBuilder == null) return null;
    final int[] indices = myList.getSelectedIndices();

    final ArrayList<PsiElement> elements = new ArrayList<PsiElement>();
    for (int index : indices) {
      final PsiElement element = getSelectedElement(index);
      if (element != null) {
        elements.add(element);
      }
    }

    return elements.toArray(new PsiElement[elements.size()]);
  }

  private Object getValueAtIndex(AbstractTreeNode node) {
    if (node == null) return null;
    Object value = node.getValue();
    if (value instanceof TreeElement){
      return ((StructureViewTreeElement)value).getValue();
    }
    return value;
  }

  final void setActive(final boolean active) {
    if (active) {
      myTitlePanel.setBackground(DARK_BLUE);
      myTitlePanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, DARK_BLUE_BRIGHTER, DARK_BLUE_DARKER));
      myParentTitle.setForeground(Color.white);
    }
    else {
      final Color color = UIUtil.getPanelBackground();
      LOG.assertTrue(color != null);
      myTitlePanel.setBackground(color);
      myTitlePanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, color.brighter(), color.darker()));
      myParentTitle.setForeground(Color.black);
    }
    final int[] selectedIndices = myList.getSelectedIndices();
    if (selectedIndices.length == 0 && myList.getModel().getSize() > 0) {
      myList.setSelectedIndex(0);
      if (!myList.hasFocus()) {
        myList.requestFocus();
      }
    }
    else if (myList.getModel().getSize() > 0){
      // need this to generate SelectionChanged events so that listeners, added by Commander, will be notified
      myList.setSelectedIndices(selectedIndices);
    }
  }

  public final Commander getCommander() {
    return myCommander;
  }

  private void invokePopup(final Component c, final int x, final int y) {
    if (myCommander == null) return;
    if (myBuilder == null) return;

    if (myList.getSelectedIndices().length <= 1) {
      final int popupIndex = myList.locationToIndex(new Point(x, y));
      if (popupIndex >= 0) {
        myList.setSelectedIndex(popupIndex);
        myList.requestFocus();
      }
    }

    final ActionGroup group = (ActionGroup)CustomizableActionsSchemas.getInstance().getCorrectedAction(IdeActions.GROUP_COMMANDER_POPUP);
    final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.COMMANDER_POPUP, group);
    popupMenu.getComponent().show(c, x, y);
  }

  public final void dispose() {
    if (myBuilder != null) {
      myBuilder.dispose();
      myBuilder = null;
    }
    myProject = null;
  }

  public final void setTitlePanelVisible(final boolean flag) {
    myTitlePanel.setVisible(flag);
  }

  public final Object getDataImpl(final String dataId) {
    if (myBuilder == null) return null;
    final Object selectedValue = getSelectedValue();
    if (DataConstants.PSI_ELEMENT.equals(dataId)) {
      final PsiElement selectedElement = getSelectedElement();
      return (selectedElement != null && selectedElement.isValid())? selectedElement : null;
    }
    if (DataConstantsEx.PSI_ELEMENT_ARRAY.equals(dataId)) {
      return filterInvalidElements(getSelectedElements());
    }
    else if (DataConstantsEx.PASTE_TARGET_PSI_ELEMENT.equals(dataId)) {
      final AbstractTreeNode parentNode = myBuilder.getParentNode();
      final Object element = parentNode != null? parentNode.getValue() : null;
      return (element instanceof PsiElement) && ((PsiElement)element).isValid() ? element : null;
    }
    else if (DataConstants.NAVIGATABLE_ARRAY.equals(dataId)) {
      return getNavigatables();
    }
    else if (DataConstantsEx.COPY_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator != null ? myCopyPasteDelegator.getCopyProvider() : null;
    }
    else if (DataConstantsEx.CUT_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator != null ? myCopyPasteDelegator.getCutProvider() : null;
    }
    else if (DataConstantsEx.PASTE_PROVIDER.equals(dataId)) {
      return myCopyPasteDelegator != null ? myCopyPasteDelegator.getPasteProvider() : null;
    }
    else if (DataConstantsEx.IDE_VIEW.equals(dataId)) {
      return myIdeView;
    }
    else if (DataConstantsEx.DELETE_ELEMENT_PROVIDER.equals(dataId)) {
      return myDeleteElementProvider;
    } else if (DataConstants.MODULE.equals(dataId)){
      return selectedValue instanceof Module ? selectedValue : null;
    } else if (DataConstantsEx.MODULE_GROUP_ARRAY.equals(dataId)){
      return selectedValue instanceof ModuleGroup ? new ModuleGroup[]{(ModuleGroup)selectedValue} : null;
    } else if (DataConstantsEx.GUI_DESIGNER_FORM_ARRAY.equals(dataId)){
      return selectedValue instanceof Form ? new Form[]{(Form)selectedValue} : null;
    } else if (DataConstantsEx.LIBRARY_GROUP_ARRAY.equals(dataId)){
      return selectedValue instanceof LibraryGroupElement ? new LibraryGroupElement[] {(LibraryGroupElement)selectedValue} : null;
    } else if (DataConstantsEx.NAMED_LIBRARY_ARRAY.equals(dataId)){
      return selectedValue instanceof NamedLibraryElement ? new NamedLibraryElement[]{(NamedLibraryElement)selectedValue} : null;
    }

    if (myProjectTreeStructure != null) {
      return myProjectTreeStructure.getDataFromProviders(Collections.singletonList(getSelectedNode()), dataId);
    }

    return null;
  }

  private Navigatable[] getNavigatables() {
    if (myBuilder == null) return null;
    final int[] indices = myList.getSelectedIndices();
    if (indices == null || indices.length == 0) return null;

    final ArrayList<Navigatable> elements = new ArrayList<Navigatable>();
    for (int index : indices) {
      final Object element = myModel.getElementAt(index);
      if (element instanceof AbstractTreeNode) {
        elements.add((Navigatable)element);
      }
    }

    return elements.toArray(new Navigatable[elements.size()]);

  }

  @Nullable private static PsiElement[] filterInvalidElements(final PsiElement[] elements) {
    if (elements == null || elements.length == 0) {
      return null;
    }
    final java.util.List<PsiElement> validElements = new ArrayList<PsiElement>(elements.length);
    for (final PsiElement element : elements) {
      if (element.isValid()) {
        validElements.add(element);
      }
    }
    return (validElements.size() == elements.length)? elements : validElements.toArray(new PsiElement[validElements.size()]);
  }

  protected final Navigatable createEditSourceDescriptor() {
    return EditSourceUtil.getDescriptor(getSelectedElement());
  }

  public void setProjectTreeStructure(final ProjectAbstractTreeStructureBase projectTreeStructure) {
    myProjectTreeStructure = projectTreeStructure;
  }

  private static final class MyTitleLabel extends JLabel {
    private final JPanel myPanel;

    public MyTitleLabel(final JPanel panel) {
      myPanel = panel;
    }

    public void setText(String text) {
      if (text == null || text.length() == 0) {
        text = " ";
      }
      super.setText(text);
      if (myPanel != null) {
        myPanel.setToolTipText(text.trim().length() == 0? null : text);
      }
    }
  }

  private final class MyDeleteElementProvider implements DeleteProvider {
    public void deleteElement(final DataContext dataContext) {
      final com.intellij.openapi.localVcs.LvcsAction action = LvcsIntegration.checkinFilesBeforeRefactoring(myProject,
                                                                                                      IdeBundle.message("progress.deleting"));
      try {
        final PsiElement[] elements = getSelectedElements();
        DeleteHandler.deletePsiElement(elements, myProject);
      }
      finally {
        LvcsIntegration.checkinFilesAfterRefactoring(myProject, action);
      }
    }

    public boolean canDeleteElement(final DataContext dataContext) {
      final PsiElement[] elements = getSelectedElements();
      return DeleteHandler.shouldEnableDeleteAction(elements);
    }
  }

  private final class MyIdeView implements IdeView {
    public void selectElement(final PsiElement element) {
      final boolean isDirectory = element instanceof PsiDirectory;
      if (!isDirectory) {
        EditorHelper.openInEditor(element);
      }
      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            myBuilder.selectElement(element, PsiUtil.getVirtualFile(element));
            if (!isDirectory) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                        public void run() {
                          if (Commander.getInstance(myProject).MOVE_FOCUS) {
                            ToolWindowManager.getInstance(myProject).activateEditorComponent();
                          }
                        }
                      });
            }
          }
        }, ModalityState.NON_MMODAL);
    }

    private PsiDirectory getDirectory() {
      if (myBuilder == null) return null;
      final Object parentElement = myBuilder.getParentNode();
      if (parentElement instanceof AbstractTreeNode) {
        final AbstractTreeNode parentNode = ((AbstractTreeNode)parentElement);
        if (!(parentNode.getValue() instanceof PsiDirectory)) return null;
        return (PsiDirectory)parentNode.getValue();
      } else {
        return null;
      }
    }

    public PsiDirectory[] getDirectories() {
      PsiDirectory directory = getDirectory();
      return directory == null ? PsiDirectory.EMPTY_ARRAY : new PsiDirectory[] {directory};
    }

    public PsiDirectory getOrChooseDirectory() {
      return com.intellij.ide.util.PackageUtil.getOrChooseDirectory(this);
    }
  }
}