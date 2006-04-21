package com.intellij.util.xml.tree;

import com.intellij.javaee.J2EEBundle;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import jetbrains.fabrique.ui.treeStructure.SimpleNode;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class DomElementsGroupNode extends AbstractDomElementNode {
    private DomElement myParentElement;
    private String myChildrenTagName;
    private DomCollectionChildDescription myChildDescription;

    public DomElementsGroupNode(final DomElement modelElement, DomCollectionChildDescription description) {
        myParentElement = modelElement;
        myChildDescription = description;
        myChildrenTagName = description.getXmlElementName();
    }

    public SimpleNode[] getChildren() {
        if (!myParentElement.isValid()) return NO_CHILDREN;

        final List<? extends DomElement> domChildren = myChildDescription.getValues(myParentElement);
        final List<SimpleNode> simpleNodes = new ArrayList<SimpleNode>();
        for (DomElement domChild : domChildren) {
            if (shouldBeShowed(domChild.getDomElementType())) {
                simpleNodes.add(new BaseDomElementNode(domChild, this));
            }
        }
        return simpleNodes.toArray(new SimpleNode[simpleNodes.size()]);
    }

    public Object[] getEqualityObjects() {
        return new Object[]{myParentElement, myChildrenTagName};
    }

    protected boolean doUpdate() {
        setUniformIcon(getNodeIcon());

        clearColoredText();

        final boolean showErrors = !isExpanded() && hasErrors();
        final int childrenCount = getChildren().length;

        if (childrenCount > 0) {
            addColoredFragment(getNodeName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, showErrors ? SimpleTextAttributes
              .ERROR_ATTRIBUTES.getFgColor() : SimpleTextAttributes
              .REGULAR_ATTRIBUTES
              .getFgColor()));

            addColoredFragment(" (" + childrenCount + ')',
                               showErrors ? J2EEBundle.message("dom.elements.tree.childs.contain.errors") : null,
                               SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
        } else {
            addColoredFragment(getNodeName(), SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
        }

        return true;
    }

    private boolean hasErrors() {
        for (DomElement domElement : myChildDescription.getValues(myParentElement)) {
            if (DomElementAnnotationsManager.getInstance().getProblems(domElement, true).size() > 0) return true;
        }

        return false;
    }

    public String getNodeName() {
        return myChildDescription.getCommonPresentableName(myParentElement);
    }

    public String getTagName() {
        return myChildrenTagName;
    }

    public DomElement getDomElement() {
        return myParentElement;
    }


    public DomCollectionChildDescription getChildDescription() {
        return myChildDescription;
    }

    public Icon getNodeIcon() {
        return ElementPresentationManager.getIcon(DomUtil.getRawType(myChildDescription.getType()));
    }
}
