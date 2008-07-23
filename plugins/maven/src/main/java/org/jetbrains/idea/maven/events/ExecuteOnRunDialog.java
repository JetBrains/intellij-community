package org.jetbrains.idea.maven.events;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.StringSetSpinAllocator;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Set;

/**
 * @author Vladislav.Kaznacheev
 */

public abstract class ExecuteOnRunDialog extends DialogWrapper {
  private DefaultMutableTreeNode myRoot;
  private RunManager myRunManager;

  public ExecuteOnRunDialog(final Project project, final String title) {
    super(project, true);
    myRunManager = RunManager.getInstance(project);
    setTitle(title);
    init();    
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    myRoot = buildNodes();
    final Tree tree = new Tree(myRoot);

    final MyTreeCellRenderer cellRenderer = new MyTreeCellRenderer();

    tree.setCellRenderer(cellRenderer);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setLineStyleAngled();
//    TreeToolTipHandler.install(tree);
    TreeUtil.installActions(tree);
//    new TreeSpeedSearch(tree);

    tree.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        int row = tree.getRowForLocation(e.getX(), e.getY());
        if (row >= 0) {
          Rectangle rowBounds = tree.getRowBounds(row);
          cellRenderer.setBounds(rowBounds);
          Rectangle checkBounds = cellRenderer.myCheckbox.getBounds();

          checkBounds.setLocation(rowBounds.getLocation());
          if (checkBounds.contains(e.getPoint())) {
            toggleNode(tree, (DefaultMutableTreeNode)tree.getPathForRow(row).getLastPathComponent());
            e.consume();
            tree.setSelectionRow(row);
          }
        }
      }
    });

    tree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
          TreePath treePath = tree.getLeadSelectionPath();
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
          toggleNode(tree, node);
          e.consume();
        }
      }
    });

    expandChecked(tree);

    JScrollPane scrollPane = new JScrollPane(tree);
    scrollPane.setPreferredSize(new Dimension(400, 400));
    panel.add(scrollPane, BorderLayout.CENTER);
    return panel;
  }

  private static void expandChecked(Tree tree) {
    TreeNode root = (TreeNode)tree.getModel().getRoot();
    Enumeration factories = root.children();
    ArrayList<TreeNode[]> toExpand = new ArrayList<TreeNode[]>();
    while (factories.hasMoreElements()) {
      DefaultMutableTreeNode factoryNode = (DefaultMutableTreeNode)factories.nextElement();
      Enumeration configurations = factoryNode.children();
      while (configurations.hasMoreElements()) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)configurations.nextElement();
        ConfigurationDescriptor config = (ConfigurationDescriptor)node.getUserObject();
        if (config.isChecked()) {
          toExpand.add(factoryNode.getPath());
          break;
        }
      }
    }
    for (TreeNode[] treeNodes : toExpand) {
      tree.expandPath(new TreePath(treeNodes));
    }
  }

  private static void toggleNode(JTree tree, DefaultMutableTreeNode node) {
    Descriptor descriptor = (Descriptor)node.getUserObject();
    descriptor.setChecked(!descriptor.isChecked());
    tree.repaint();
  }

  private DefaultMutableTreeNode buildNodes() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode(new Descriptor());
    ConfigurationType[] configurationFactories = myRunManager.getConfigurationFactories();

    for (final ConfigurationType type : configurationFactories) {
      final Icon icon = type.getIcon();
      DefaultMutableTreeNode typeNode =
        new DefaultMutableTreeNode(new ConfigurationTypeDescriptor(type, icon, isAssigned(type, null)));
      root.add(typeNode);
      final Set<String> addedNames = StringSetSpinAllocator.alloc();
      try {
        RunConfiguration[] configurations = myRunManager.getConfigurations(type);
        for (final RunConfiguration configuration : configurations) {
          final String configurationName = configuration.getName();
          if (addedNames.contains(configurationName)) {
            // add only the first configuration if more than one has the same name
            continue;
          }
          addedNames.add(configurationName);
          typeNode.add(new DefaultMutableTreeNode(
            new ConfigurationDescriptor(type, configurationName, isAssigned(type, configurationName))));
        }
      }
      finally {
        StringSetSpinAllocator.dispose(addedNames);
      }
    }

    return root;
  }

  abstract protected boolean isAssigned(ConfigurationType type, String configurationName);

  abstract protected void clearAll();

  abstract protected void assign (ConfigurationType type, String configurationName);



  protected void doOKAction() {
    clearAll();

    Enumeration nodes = myRoot.depthFirstEnumeration();
    while (nodes.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)nodes.nextElement();
      Descriptor descriptor = (Descriptor)node.getUserObject();
      if (!descriptor.isChecked()) continue;
      if (descriptor instanceof DescriptorBase) {
        DescriptorBase descriptorBase = (DescriptorBase)descriptor;
        assign ( descriptorBase.getConfigurationType(), descriptorBase.getName());
      }
    }

    close(OK_EXIT_CODE);
  }

  private static class Descriptor {
    private boolean myChecked;

    public final boolean isChecked() {
      return myChecked;
    }

    public final void setChecked(boolean checked) {
      myChecked = checked;
    }
  }

  private static abstract class DescriptorBase extends Descriptor {
    private final ConfigurationType myConfigurationType;

    public DescriptorBase(ConfigurationType type, boolean isChecked) {
      myConfigurationType = type;
      setChecked(isChecked);
    }

    public ConfigurationType getConfigurationType() {
      return myConfigurationType;
    }

    @Nullable
    public abstract String getName();
  }

  private static final class ConfigurationTypeDescriptor extends DescriptorBase {
    private final Icon myIcon;

    public ConfigurationTypeDescriptor(ConfigurationType type, Icon icon, boolean isChecked) {
      super(type, isChecked);
      myIcon = icon;
    }

    public Icon getIcon() {
      return myIcon;
    }

    @Nullable
    public String getName() {
      return null;
    }
  }

  private static final class ConfigurationDescriptor extends DescriptorBase {
    private final String myName;

    public ConfigurationDescriptor(ConfigurationType type, String name, boolean isChecked) {
      super(type, isChecked);
      myName = name;
    }

    public String getName() {
      return myName;
    }
  }


  private static final class MyTreeCellRenderer extends JPanel implements TreeCellRenderer {
    private final JLabel myLabel;
    public final JCheckBox myCheckbox;

    public MyTreeCellRenderer() {
      super(new BorderLayout());
      myCheckbox = new JCheckBox();
      myLabel = new JLabel();
      add(myCheckbox, BorderLayout.WEST);
      add(myLabel, BorderLayout.CENTER);
    }

    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      Descriptor descriptor = (Descriptor)node.getUserObject();

      myCheckbox.setSelected(descriptor.isChecked());

      myCheckbox.setBackground(UIUtil.getTreeTextBackground());
      setBackground(selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground());
      Color foreground = selected ? UIUtil.getTreeSelectonForeground() : UIUtil.getTreeTextForeground();
      setForeground(foreground);
      myCheckbox.setForeground(foreground);
      myLabel.setForeground(foreground);
      myCheckbox.setEnabled(true);

      if (descriptor instanceof ConfigurationTypeDescriptor) {
        ConfigurationTypeDescriptor configurationTypeDescriptor = (ConfigurationTypeDescriptor)descriptor;
        myLabel.setFont(tree.getFont());
        myLabel.setText(configurationTypeDescriptor.getConfigurationType().getDisplayName());
        myLabel.setIcon(configurationTypeDescriptor.getIcon());
      }
      else if (descriptor instanceof ConfigurationDescriptor) {
        ConfigurationDescriptor configurationTypeDescriptor = (ConfigurationDescriptor)descriptor;
        myLabel.setFont(tree.getFont());
        myLabel.setText(configurationTypeDescriptor.getName());
        myLabel.setIcon(null);

        if (((ConfigurationTypeDescriptor)((DefaultMutableTreeNode)node.getParent()).getUserObject()).isChecked()) {
          Color foregrnd = tree.getForeground();
          Color backgrnd = tree.getBackground();
          if (foregrnd == null) foregrnd = Color.black;
          if (backgrnd == null) backgrnd = Color.white;

          int red = (foregrnd.getRed() + backgrnd.getRed()) / 2;
          int green = (foregrnd.getGreen() + backgrnd.getGreen()) / 2;
          int blue = (foregrnd.getBlue() + backgrnd.getBlue()) / 2;
          Color halftone = new Color(red, green, blue);
          setForeground(halftone);
          myCheckbox.setForeground(halftone);
          myLabel.setForeground(halftone);
          myCheckbox.setEnabled(false);
        }
      }

      return this;
    }
  }
}
