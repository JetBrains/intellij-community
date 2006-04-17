package com.intellij.util.xml.tree;

import com.intellij.openapi.util.Key;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ui.TooltipUtils;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import com.intellij.lang.annotation.HighlightSeverity;
import jetbrains.fabrique.ui.treeStructure.SimpleNode;

import java.util.*;


public class BaseDomElementNode extends AbstractDomElementNode {
  public static final Key<Comparator> COMPARATOR_KEY = Key.create("COMPARATOR_KEY");
  public static final Key<Boolean> SHOW_PROPERTIES_KEY = Key.create("SHOW_PROPERTIES_KEY");

  private final DomElement myDomElement;
  private final String myTagName;


  public BaseDomElementNode(final DomElement modelElement) {
    this(modelElement, null);
  }

  public BaseDomElementNode(final DomElement modelElement, SimpleNode parent) {
    super(parent);

    myDomElement = modelElement;
    myTagName = modelElement.getXmlElementName();
  }

  public SimpleNode[] getChildren() {
    return doGetChildren(myDomElement);
  }

  protected final SimpleNode[] doGetChildren(final DomElement element) {
    if (!element.isValid()) return NO_CHILDREN;

    List<SimpleNode> children = new ArrayList<SimpleNode>();

    for (DomFixedChildDescription description : element.getGenericInfo().getFixedChildrenDescriptions()) {
      final List<? extends DomElement> values = description.getValues(element);
      if (shouldBeShowed(description.getType())) {
        if (DomUtil.isGenericValueType(description.getType())) {
          for (DomElement domElement : values) {
            children.add(new GenericValueNode((GenericDomValue)domElement, this));
          }
        } else {
          for (DomElement domElement : values) {
            children.add(new BaseDomElementNode(domElement, this));
          }
        }
      }
    }

    final List<DomCollectionChildDescription> collectionChildrenDescriptions = element.getGenericInfo().getCollectionChildrenDescriptions();
    for (DomCollectionChildDescription description : collectionChildrenDescriptions) {
      if (shouldBeShowed(description.getType())) {
        children.add(new DomElementsGroupNode(element, description));
      }
    }

    AbstractDomElementNode[] childrenNodes = children.toArray(new AbstractDomElementNode[children.size()]);

    final Comparator<AbstractDomElementNode> comparator = myDomElement.getRoot().getUserData(COMPARATOR_KEY);
    if (comparator != null) {
      Arrays.sort(childrenNodes, comparator);
    }

    return childrenNodes;
  }

  protected boolean showGenericValues() {
    final Boolean showProperties = myDomElement.getRoot().getUserData(SHOW_PROPERTIES_KEY);
    return showProperties != null && showProperties;
  }

  public Object[] getEqualityObjects() {
    return new Object[]{myDomElement};
  }

  protected boolean doUpdate() {
    if (!myDomElement.isValid()) return true;

    setUniformIcon(getNodeIcon());
    clearColoredText();
    boolean isExpanded = isExpanded();

    final List<DomElementProblemDescriptor> problems = DomElementAnnotationsManager.getInstance().getProblems(myDomElement, true, highlightIfChildrenHasProblems(), HighlightSeverity.ERROR);
    if (problems.size() > 0) {
      addColoredFragment(getNodeName(), TooltipUtils.getTooltipText(problems), SimpleTextAttributes.ERROR_ATTRIBUTES); //new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, Color.BLACK, Color.RED)
    } else  if (myDomElement.getXmlTag() != null) {
      addColoredFragment(getNodeName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    } else {
      addColoredFragment(getNodeName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    return true;
  }

  protected boolean highlightIfChildrenHasProblems() {
    return true;
  }

  public String getNodeName() {
    final String name = myDomElement.getPresentation().getElementName();
    return name != null ? name : getPropertyName();
  }

  public String getTagName() {
    return myTagName;
  }

  public DomElement getDomElement() {
    return myDomElement;
  }

  public boolean isAutoExpandNode() {
    return getParent() == null;
  }
}
