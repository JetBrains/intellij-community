/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.ui.treeStructure;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import com.intellij.util.ui.update.ComparableObject;
import com.intellij.util.ui.update.ComparableObjectCheck;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;

public abstract class SimpleNode extends NodeDescriptor implements ComparableObject {

  protected static final SimpleNode[] NO_CHILDREN = new SimpleNode[0];

  protected List<ColoredFragment> myColoredText = new ArrayList<ColoredFragment>();
  private int myWeight = 10;
  private Font myFont;

  protected SimpleNode(Project project) {
    this(project, null);
  }

  protected SimpleNode(Project project, NodeDescriptor parentDescriptor) {
    super(project, parentDescriptor);
    myName = "";
  }

  protected SimpleNode(SimpleNode parent) {
    this(parent == null ? null : parent.myProject, parent);
  }

  protected SimpleNode() {
    super(null, null);
  }

  public String toString() {
    return getName();
  }

  public int getWeight() {
    return myWeight;
  }

  protected SimpleTextAttributes getErrorAttributes() {
    return new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, getColor(), Color.red);
  }

  protected SimpleTextAttributes getPlainAttributes() {
    return new SimpleTextAttributes(Font.PLAIN, getColor());
  }

  public final void setWeight(int weight) {
    myWeight = weight;
  }

  protected FileStatus getFileStatus() {
    return FileStatus.NOT_CHANGED;
  }

  public final boolean update() {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        myColor = Color.black;
        assert getFileStatus() != null: getClass().getName() + ' ' + toString();
        Color fileStatusColor = getFileStatus().getColor();
        if (fileStatusColor != null) {
          myColor = fileStatusColor;
        }

        final boolean result = doUpdate();
        myName = getName();

        if (SimpleNode.this instanceof DeletableNode) {
          DeletableNode deletableNode = (DeletableNode) SimpleNode.this;
          if (deletableNode.isReadOnly()) {
            makeIconsReadOnly();
          }
        }

        return Boolean.valueOf(result);
      }
    }).booleanValue();
  }

  private void makeIconsReadOnly() {
    myOpenIcon = makeIconReadOnly(myOpenIcon);
    myClosedIcon = makeIconReadOnly(myClosedIcon);
  }

  private Icon makeIconReadOnly(Icon icon) {
    if (icon != null) {
      LayeredIcon layeredIcon = new LayeredIcon(2);
      layeredIcon.setIcon(icon, 0);
      layeredIcon.setIcon(Icons.LOCKED_ICON, 1);
      return layeredIcon;
    }
    return icon;
  }

  public final String getName() {
    StringBuffer result = new StringBuffer("");
    for (int i = 0; i < myColoredText.size(); i++) {
      ColoredFragment each = myColoredText.get(i);
      result.append(each.getText());
    }
    return result.toString();
  }

  public final void setNodeText(String text, String tooltip, boolean hasError){
    clearColoredText();
    SimpleTextAttributes attributes = hasError ? getErrorAttributes() : getPlainAttributes();
    myColoredText.add(new ColoredFragment(text, tooltip, attributes));
  }

  public final void setPlainText(String aText) {
    clearColoredText();
    addPlainText(aText);
  }

  public final void addPlainText(String aText) {
    myColoredText.add(new ColoredFragment(aText, getPlainAttributes()));
  }

  public final void addErrorText(String aText, String errorTooltipText) {
    myColoredText.add(new ColoredFragment(aText, errorTooltipText, getErrorAttributes()));
  }

  public final void clearColoredText() {
    myColoredText.clear();
  }

  public final void addColoredFragment(String aText, SimpleTextAttributes aAttributes) {
    addColoredFragment(aText, null, aAttributes);
  }

  public final void addColoredFragment(String aText, String toolTip, SimpleTextAttributes aAttributes) {
    myColoredText.add(new ColoredFragment(aText, toolTip, aAttributes));
  }

  public final void addColoredFragment(ColoredFragment fragment) {
    myColoredText.add(new ColoredFragment(fragment.getText(), fragment.getAttributes()));
  }

  protected boolean doUpdate() {
    return false;
  }

  public final Object getElement() {
    return this;
  }

  public final SimpleNode getParent() {
    return (SimpleNode) getParentDescriptor();
  }

  public abstract SimpleNode[] getChildren();

  public void accept(SimpleNodeVisitor visitor) {
    visitor.accept(this);
  }

  public void handleSelection(SimpleTree tree) {
  }

  public void handleDoubleClickOrEnter(SimpleTree tree, InputEvent inputEvent) {
  }

  public boolean isAlwaysShowPlus() {
    return false;
  }

  public boolean isAutoExpandNode() {
    return false;
  }

  public boolean shouldHaveSeparator() {
    return false;
  }

  public void setUniformIcon(Icon aIcon) {
    setIcons(aIcon, aIcon);
  }

  public final void setIcons(Icon aClosed, Icon aOpen) {
    myOpenIcon = aOpen;
    myClosedIcon = aClosed;
  }

  public final ColoredFragment[] getColoredText() {
    return myColoredText.toArray(new ColoredFragment[myColoredText.size()]);
  }

  public static class ColoredFragment {
    private String myText;
    private String myToolTip;
    private SimpleTextAttributes myAttributes;

    public ColoredFragment(String aText, SimpleTextAttributes aAttributes) {
      this(aText, null, aAttributes);
    }

    public ColoredFragment(String aText, String toolTip, SimpleTextAttributes aAttributes) {
      myText = aText;
      myAttributes = aAttributes;
      myToolTip = toolTip;
    }

    public String getToolTip() {
      return myToolTip;
    }

    public String getText() {
      return myText;
    }

    public SimpleTextAttributes getAttributes() {
      return myAttributes;
    }
  }

  public boolean isAncestorOrSelf(SimpleNode selectedNode) {
    SimpleNode node = selectedNode;
    while (node != null) {
      if (equals(node)) return true;
      node = node.getParent();
    }
    return false;
  }

  public Font getFont() {
    return myFont;
  }

  public void setFont(Font font) {
    myFont = font;
  }


  public final boolean equals(Object o) {
    return ComparableObjectCheck.equals(this, o);
  }

  public final int hashCode() {
    return ComparableObjectCheck.hashCode(this, super.hashCode());
  }

  public Object[] getSelectionEqualityObjects() {
    return new Object[] {this};
  }



}
