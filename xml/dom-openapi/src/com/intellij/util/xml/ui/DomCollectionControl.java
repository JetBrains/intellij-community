/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.xml.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlElement;
import com.intellij.ui.CommonActionsPanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.IconUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.highlighting.DomCollectionProblemDescriptor;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.ui.actions.AddDomElementAction;
import com.intellij.util.xml.ui.actions.DefaultAddAction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.intellij.util.ObjectUtils.assertNotNull;

/**
 * @author peter
 */
public class DomCollectionControl<T extends DomElement> extends DomUIControl implements Highlightable, TypeSafeDataProvider {
  private static final DataKey<DomCollectionControl> DOM_COLLECTION_CONTROL = DataKey.create("DomCollectionControl");

  private final EventDispatcher<CommitListener> myDispatcher = EventDispatcher.create(CommitListener.class);
  private DomTableView myCollectionPanel;

  private final DomElement myParentDomElement;
  private final DomCollectionChildDescription myChildDescription;
  private List<T> myCollectionElements = new ArrayList<>();
  private ColumnInfo<T, ?>[] myColumnInfos;
  private boolean myEditable = false;
  public static final Icon ADD_ICON = IconUtil.getAddIcon();
  public static final Icon EDIT_ICON = IconUtil.getEditIcon();
  public static final Icon REMOVE_ICON = IconUtil.getRemoveIcon();

  public DomCollectionControl(@NotNull DomElement parentElement,
                              @NotNull DomCollectionChildDescription description,
                              final boolean editable,
                              ColumnInfo<T, ?>... columnInfos) {
    myChildDescription = description;
    myParentDomElement = parentElement;
    myColumnInfos = columnInfos;
    myEditable = editable;
  }

  public DomCollectionControl(DomElement parentElement,
                              @NonNls String subTagName,
                              final boolean editable,
                              ColumnInfo<T, ?>... columnInfos) {
    this(parentElement, assertNotNull(parentElement.getGenericInfo().getCollectionChildDescription(subTagName)), editable, columnInfos);
  }

  public DomCollectionControl(@NotNull DomElement parentElement, @NotNull DomCollectionChildDescription description) {
    myChildDescription = description;
    myParentDomElement = parentElement;
  }

  public DomCollectionControl(@NotNull DomElement parentElement, @NotNull @NonNls String subTagName) {
    this(parentElement, assertNotNull(parentElement.getGenericInfo().getCollectionChildDescription(subTagName)));
  }

  public boolean isEditable() {
    return myEditable;
  }

  @Override
  public void bind(JComponent component) {
    assert component instanceof DomTableView;

    initialize((DomTableView)component);
  }

  @Override
  public void addCommitListener(CommitListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void removeCommitListener(CommitListener listener) {
    myDispatcher.removeListener(listener);
  }


  @Override
  public boolean canNavigate(DomElement element) {
    final Class<DomElement> aClass = (Class<DomElement>)ReflectionUtil.getRawType(myChildDescription.getType());

    final DomElement domElement = element.getParentOfType(aClass, false);

    return domElement != null && myCollectionElements.contains(domElement);
  }

  @Override
  public void navigate(DomElement element) {
    final Class<DomElement> aClass = (Class<DomElement>)ReflectionUtil.getRawType(myChildDescription.getType());
    final DomElement domElement = element.getParentOfType(aClass, false);

    int index = myCollectionElements.indexOf(domElement);
    if (index < 0) index = 0;

    myCollectionPanel.getTable().setRowSelectionInterval(index, index);
  }

  @Override
  public void calcData(final DataKey key, final DataSink sink) {
    if (DOM_COLLECTION_CONTROL.equals(key)) {
      sink.put(DOM_COLLECTION_CONTROL, this);
    }
  }

  @Nullable
  protected String getHelpId() {
    return null;
  }

  @Nullable
  protected String getEmptyPaneText() {
    return null;
  }

  protected void initialize(final DomTableView boundComponent) {
    if (boundComponent == null) {
      myCollectionPanel = new DomTableView(getProject(), getEmptyPaneText(), getHelpId());
    }
    else {
      myCollectionPanel = boundComponent;
    }
    myCollectionPanel.setToolbarActions(new AddAction(), new EditAction(), new RemoveAction());
    myCollectionPanel.installPopup(ActionPlaces.J2EE_ATTRIBUTES_VIEW_POPUP, createPopupActionGroup());
    myCollectionPanel.initializeTable();
    myCollectionPanel.addCustomDataProvider(this);
    myCollectionPanel.addChangeListener(new AbstractTableView.ChangeListener() {
      @Override
      public void changed() {
        reset();
      }
    });
    reset();
  }

  protected DefaultActionGroup createPopupActionGroup() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.addAll((DefaultActionGroup)ActionManager.getInstance().getAction("DomCollectionControl"));
    return group;
  }

  protected ColumnInfo[] createColumnInfos(DomElement parent) {
    return myColumnInfos;
  }


  protected int sortAdjustedIndex(final int index) {
    return myCollectionPanel.getTable().convertRowIndexToModel(index);
  }

  protected final void doEdit() {
    doEdit(myCollectionElements.get(sortAdjustedIndex(myCollectionPanel.getTable().getSelectedRow())));
  }

  protected void doEdit(final T t) {
    final DomEditorManager manager = getDomEditorManager(this);
    if (manager != null) {
      manager.openDomElementEditor(t);
    }
  }

  protected void doRemove(final List<T> toDelete) {
    Set<PsiFile> files = new HashSet<>();
    for (final T t : toDelete) {
      final XmlElement element = t.getXmlElement();
      if (element != null) {
        ContainerUtil.addIfNotNull(files, element.getContainingFile());
      }
    }

    new WriteCommandAction(getProject(), PsiUtilCore.toPsiFileArray(files)) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        for (final T t : toDelete) {
          if (t.isValid()) {
            t.undefine();
          }
        }
      }
    }.execute();
  }

  protected final void doRemove() {
    ApplicationManager.getApplication().invokeLater(() -> {
      final int[] selected = myCollectionPanel.getTable().getSelectedRows();
      if (selected == null || selected.length == 0) return;
      final List<T> selectedElements = new ArrayList<>(selected.length);
      for (final int i : selected) {
        selectedElements.add(myCollectionElements.get(sortAdjustedIndex(i)));
      }

      doRemove(selectedElements);
      reset();
      int selection = selected[0];
      if (selection >= myCollectionElements.size()) {
        selection = myCollectionElements.size() - 1;
      }
      if (selection >= 0) {
        myCollectionPanel.getTable().setRowSelectionInterval(selection, selection);
      }
    });
  }

  @Override
  public void commit() {
    final CommitListener listener = myDispatcher.getMulticaster();
    listener.beforeCommit(this);
    listener.afterCommit(this);
    validate();
  }

  private void validate() {
    DomElement domElement = getDomElement();
    final List<DomElementProblemDescriptor> list =
      DomElementAnnotationsManager.getInstance(getProject()).getCachedProblemHolder(domElement).getProblems(domElement);
    final List<String> messages = new ArrayList<>();
    for (final DomElementProblemDescriptor descriptor : list) {
      if (descriptor instanceof DomCollectionProblemDescriptor
          && myChildDescription.equals(((DomCollectionProblemDescriptor)descriptor).getChildDescription())) {
        messages.add(descriptor.getDescriptionTemplate());
      }
    }
    myCollectionPanel.setErrorMessages(ArrayUtil.toStringArray(messages));
    myCollectionPanel.repaint();
  }

  @Override
  public void dispose() {
    if (myCollectionPanel != null) {
      myCollectionPanel.dispose();
    }
  }

  protected final Project getProject() {
    return myParentDomElement.getManager().getProject();
  }

  @Override
  public DomTableView getComponent() {
    if (myCollectionPanel == null) initialize(null);

    return myCollectionPanel;
  }

  public final DomCollectionChildDescription getChildDescription() {
    return myChildDescription;
  }

  @Override
  public final DomElement getDomElement() {
    return myParentDomElement;
  }

  @Override
  public final void reset() {
    myCollectionElements = new ArrayList<>(getCollectionElements());
    myCollectionPanel.reset(createColumnInfos(myParentDomElement), myCollectionElements);
    validate();
  }

  public List<T> getCollectionElements() {
    return (List<T>)myChildDescription.getValues(myParentDomElement);
  }

  @Nullable
  protected AnAction[] createAdditionActions() {
    return null;
  }

  protected DefaultAddAction createDefaultAction(final String name, final Icon icon, final Type type) {
    return new ControlAddAction(name, name, icon) {
      @Override
      protected Type getElementType() {
        return type;
      }
    };
  }

  @Nullable
  private static DomEditorManager getDomEditorManager(DomUIControl control) {
    JComponent component = control.getComponent();
    while (component != null && !(component instanceof DomEditorManager)) {
      final Container parent = component.getParent();
      if (!(parent instanceof JComponent)) {
        return null;
      }
      component = (JComponent)parent;
    }
    return (DomEditorManager)component;
  }

  @Override
  public void updateHighlighting() {
    if (myCollectionPanel != null) {
      myCollectionPanel.revalidate();
      myCollectionPanel.repaint();
    }
  }

  public class ControlAddAction extends DefaultAddAction<T> {

    public ControlAddAction() {
    }

    public ControlAddAction(final String text) {
      super(text);
    }

    public ControlAddAction(final String text, final String description, final Icon icon) {
      super(text, description, icon);
    }

    @Override
    protected final DomCollectionChildDescription getDomCollectionChildDescription() {
      return myChildDescription;
    }

    @Override
    protected final DomElement getParentDomElement() {
      return myParentDomElement;
    }

    /**
     * @return negative value to disable auto-edit, or a column number where the editing should start after a new row is added
     */
    protected int getColumnToEditAfterAddition() {
      return 0;
    }

    protected void afterAddition(final JTable table, final int rowIndex) {
      table.setRowSelectionInterval(rowIndex, rowIndex);
      final int column = getColumnToEditAfterAddition();
      if (column >= 0) {
        table.editCellAt(rowIndex, column);
      }
    }

    @Override
    protected final void afterAddition(@NotNull final T newElement) {
      reset();
      afterAddition(myCollectionPanel.getTable(), myCollectionElements.size() - 1);
    }
  }

  public static DomCollectionControl getDomCollectionControl(final AnActionEvent e) {
    return e.getData(DOM_COLLECTION_CONTROL);
  }

  public static class AddAction extends AddDomElementAction {

    public AddAction() {
      setShortcutSet(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD));
    }

    @Override
    protected boolean isEnabled(final AnActionEvent e) {
      return getDomCollectionControl(e) != null;
    }

    protected DomCollectionControl getDomCollectionControl(final AnActionEvent e) {
      return DomCollectionControl.getDomCollectionControl(e);
    }

    @Override
    @NotNull
    protected DomCollectionChildDescription[] getDomCollectionChildDescriptions(final AnActionEvent e) {
      return new DomCollectionChildDescription[]{getDomCollectionControl(e).getChildDescription()};
    }

    @Override
    protected DomElement getParentDomElement(final AnActionEvent e) {
      return getDomCollectionControl(e).getDomElement();
    }

    @Override
    protected JComponent getComponent(AnActionEvent e) {
      return getDomCollectionControl(e).getComponent();
    }

    @Override
    @NotNull
    public AnAction[] getChildren(final AnActionEvent e) {
      final DomCollectionControl control = getDomCollectionControl(e);
      AnAction[] actions = control.createAdditionActions();
      return actions == null ? super.getChildren(e) : actions;
    }

    @Override
    protected DefaultAddAction createAddingAction(final AnActionEvent e,
                                                  final String name,
                                                  final Icon icon,
                                                  final Type type,
                                                  final DomCollectionChildDescription description) {
      return getDomCollectionControl(e).createDefaultAction(name, icon, type);
    }
  }

  public static class EditAction extends AnAction {

    public EditAction() {
      super(ApplicationBundle.message("action.edit"), null, EDIT_ICON);
      setShortcutSet(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.EDIT));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final DomCollectionControl control = getDomCollectionControl(e);
      control.doEdit();
      control.reset();
    }

    @Override
    public void update(AnActionEvent e) {
      final DomCollectionControl control = getDomCollectionControl(e);
      final boolean visible = control != null && control.isEditable();
      e.getPresentation().setVisible(visible);
      e.getPresentation().setEnabled(visible && control.getComponent().getTable().getSelectedRowCount() == 1);
    }
  }

  public static class RemoveAction extends AnAction {
    public RemoveAction() {
      super(ApplicationBundle.message("action.remove"), null, REMOVE_ICON);
      setShortcutSet(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.REMOVE));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final DomCollectionControl control = getDomCollectionControl(e);
      control.doRemove();
      control.reset();
    }

    @Override
    public void update(AnActionEvent e) {
      final boolean enabled;
      final DomCollectionControl control = getDomCollectionControl(e);
      if (control != null) {
        final JTable table = control.getComponent().getTable();
        enabled = table != null && table.getSelectedRowCount() > 0;
      }
      else {
        enabled = false;
      }
      e.getPresentation().setEnabled(enabled);
    }
  }
}
