/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.treeStructure;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.SimpleTextAttributes;
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

  protected FileStatus getFileStatus() {
    return FileStatus.NOT_CHANGED;
  }

  protected Object updateElement() {
    return getElement();
  }

  protected boolean shouldUpdate() {
    return true;
  }

  public final boolean update() {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        Object newElement = updateElement();
        boolean changed = false;
        if (getElement() != newElement) {
          changed = true;
        }
        if (newElement == null) return changed;

        Color oldColor = myColor;
        String oldName = myName;
        Icon oldOpenIcon = myOpenIcon;
        Icon oldClosedIcon = myClosedIcon;
        List<ColoredFragment> oldFragments = new ArrayList<ColoredFragment>(myColoredText);

        myColor = Color.black;
        assert getFileStatus() != null: getClass().getName() + ' ' + toString();
        Color fileStatusColor = getFileStatus().getColor();
        if (fileStatusColor != null) {
          myColor = fileStatusColor;
        }

        doUpdate();
        myName = getName();

        return changed || !Comparing.equal(new Object[]{myOpenIcon, myClosedIcon, myName, oldFragments, myColor}, new Object[]{oldOpenIcon, oldClosedIcon, oldName, oldFragments, oldColor});
      }
    }).booleanValue();
  }

  public final String getName() {
    StringBuffer result = new StringBuffer("");
    for (ColoredFragment each : myColoredText) {
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

  protected void doUpdate() {
  }

  public Object getElement() {
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
      myText = aText == null? "" : aText;
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


    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ColoredFragment that = (ColoredFragment)o;

      if (myAttributes != null ? !myAttributes.equals(that.myAttributes) : that.myAttributes != null) return false;
      if (myText != null ? !myText.equals(that.myText) : that.myText != null) return false;
      if (myToolTip != null ? !myToolTip.equals(that.myToolTip) : that.myToolTip != null) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = (myText != null ? myText.hashCode() : 0);
      result = 31 * result + (myToolTip != null ? myToolTip.hashCode() : 0);
      result = 31 * result + (myAttributes != null ? myAttributes.hashCode() : 0);
      return result;
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
