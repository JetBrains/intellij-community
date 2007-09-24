package org.jetbrains.idea.maven.core.util;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleCircularDependencyException;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class IdeaAPIHelper {

  public static void executeAction(final String actionId, final InputEvent e) {
    final ActionManager actionManager = ActionManager.getInstance();
    final AnAction action = actionManager.getAction(actionId);
    if (action != null) {
      final Presentation presentation = new Presentation();
      final AnActionEvent event = new AnActionEvent(e, DataManager.getInstance().getDataContext(), "", presentation, actionManager, 0);
      action.update(event);
      if (presentation.isEnabled()) {
        action.actionPerformed(event);
      }
    }
  }

  public static void deleteModules(final Collection<Module> modules) {
    if (modules.isEmpty()) {
      return;
    }
    final ModifiableModuleModel model = ModuleManager.getInstance(modules.iterator().next().getProject()).getModifiableModel();
    for (Module module : modules) {
      model.disposeModule(module);
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          model.commit();
        }
        catch (ModuleCircularDependencyException ignore) {
        }
      }
    });
  }

  public static <E> void addElements(final ElementsChooser<E> chooser, final Collection<E> all, final Collection<E> selected) {
    for (E element : all) {
      chooser.addElement( element, selected.contains(element));
    }
  }

  public static <E> void addElements(ElementsChooser<E> chooser, Collection<E> all, Collection<E> selected, Comparator<E> comparator){
    final Collection<E> sorted = new TreeSet<E>(comparator);
    sorted.addAll(all);
    addElements(chooser, sorted, selected);
  }

  public static <T> boolean equalAsSets(final Collection<T> collection1, final Collection<T> collection2) {
    return setize(collection1).equals(setize(collection2));
  }

  private static <T> Collection<T> setize(final Collection<T> collection) {
    return (collection instanceof Set ? collection : new HashSet<T>(collection));
  }

  public static void installCheckboxRenderer(final SimpleTree tree, final CheckboxHandler handler) {
    final JCheckBox checkbox = new JCheckBox();

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(checkbox, BorderLayout.WEST);

    final TreeCellRenderer baseRenderer = tree.getCellRenderer();
    tree.setCellRenderer(new TreeCellRenderer() {
      public Component getTreeCellRendererComponent(final JTree tree,
                                                    final Object value,
                                                    final boolean selected,
                                                    final boolean expanded,
                                                    final boolean leaf,
                                                    final int row,
                                                    final boolean hasFocus) {
        final Component baseComponent = baseRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

        final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        if (!handler.isVisible(userObject)) {
          return baseComponent;
        }

        final Color foreground = selected ? UIUtil.getTreeSelectonForeground() : UIUtil.getTreeTextForeground();

        panel.add(baseComponent, BorderLayout.CENTER);
        panel.setBackground(selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground());
        panel.setForeground(foreground);

        checkbox.setSelected(handler.isSelected(userObject));
        checkbox.setBackground(UIUtil.getTreeTextBackground());
        checkbox.setForeground(foreground);

        return panel;
      }
    });

    tree.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        int row = tree.getRowForLocation(e.getX(), e.getY());
        if (row >= 0) {
          Rectangle checkBounds = checkbox.getBounds();
          checkBounds.setLocation(tree.getRowBounds(row).getLocation());
          if (checkBounds.contains(e.getPoint())) {
            handler.toggle(tree.getPathForRow(row), e);
            e.consume();
            tree.setSelectionRow(row);
          }
        }
      }
    });

    tree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
          TreePath[] treePaths = tree.getSelectionPaths();
          if (treePaths != null) {
            for (TreePath treePath : treePaths) {
              handler.toggle(treePath, e);
            }
            e.consume();
          }
        }
      }
    });
  }

  public interface CheckboxHandler {
    void toggle(TreePath treePath, final InputEvent e);

    boolean isVisible(Object userObject);

    boolean isSelected(Object userObject);
  }
}
