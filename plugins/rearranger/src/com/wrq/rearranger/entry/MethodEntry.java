/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.entry;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.wrq.rearranger.ModifierConstants;
import com.wrq.rearranger.popup.RearrangerTreeNode;
import com.wrq.rearranger.rearrangement.Emitter;
import com.wrq.rearranger.ruleinstance.RuleInstance;
import com.wrq.rearranger.settings.CommentRule;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.settings.RelatedMethodsSettings;
import com.wrq.rearranger.settings.attributeGroups.IHasGetterSetterDefinition;
import com.wrq.rearranger.settings.attributeGroups.IRestrictMethodExtraction;
import com.wrq.rearranger.settings.attributeGroups.Rule;
import com.wrq.rearranger.util.CommentUtil;
import com.wrq.rearranger.util.MethodUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.*;

/**
 * Corresponds to a method in the source file.
 * Contains structures and logic to handle related method rearrangement.
 */
public class MethodEntry extends ClassContentsEntry implements RelatableEntry {
// ------------------------------ FIELDS ------------------------------

  private static final Logger LOG = Logger.getInstance("#" + MethodEntry.class.getName());

  /**
   * Contains a list of methods called by this method.  One exception: if this method is a getter, then
   * the corresponding setter will be placed in calledMethods.  This allows the same code to rearrange both
   * types of related methods.
   */
  private final List<MethodEntry> myCalledMethods              = new ArrayList<MethodEntry>();
  private final List<MethodEntry> myCalledByMethods            = new ArrayList<MethodEntry>();
  private final List<MethodEntry> myOverloadedMethods          = new ArrayList<MethodEntry>();
  //    MethodEntry correspondingSetter = null; // TODO - avoid wrong level for setters
  final List<MethodEntry> myCorrespondingGetterSetters = new ArrayList<MethodEntry>();
  
  private boolean myKeptWithProperty;
  private boolean myRelatedMethod;

  /** @return true if the method is related to another.  In this case, the method is exempt from rule matching. */
  public boolean isRelatedMethod() {
    return myRelatedMethod;
  }

  /** @return true if the method is a setter and will be emitted below a corresponding getter. */
  public boolean isEmittableSetter() {
    return setter && myCorrespondingGetterSetters.size() > 0;
  }

  private       boolean isOverloadedMethod;
  private final int     nParameters;
  private String customizedPrecedingComment = "";

  public String getCustomizedPrecedingComment() {
    if (customizedPrecedingComment == null) {
      return "";
    }
    return customizedPrecedingComment;
  }

  private String customizedTrailingComment = "";

  public String getCustomizedTrailingComment() {
    if (customizedTrailingComment == null) {
      return "";
    }
    return customizedTrailingComment;
  }

  private boolean noExtractedMethods;

  public boolean isNoExtractedMethods() {
    return noExtractedMethods;
  }

  public void setNoExtractedMethods(boolean noExtractedMethods) {
    this.noExtractedMethods = noExtractedMethods;
  }

  private boolean getter;

  public boolean isGetter() {
    return getter;
  }

  public void setGetter(boolean getter) {
    this.getter = getter;
  }

  private boolean setter;

  public boolean isSetter() {
    return setter;
  }

  public void setSetter(boolean setter) {
    this.setter = setter;
  }

  /** name of interface, if this method implements an interface method. */
  private final String interfaceName;

  public String getInterfaceName() {
    return interfaceName;
  }

  private List<MethodEntry> sortedMethods = new LinkedList<MethodEntry>();

// -------------------------- STATIC METHODS --------------------------

  /**
   * Called after top level methods (and other items) have been moved from entries to ruleInstanceList.  Task now
   * is to scan ruleInstanceList looking for MethodEntry objects with dependent (related, extracted) methods.  Move
   * these from the entries list to the correct place in the ruleInstanceList list.
   *
   * @param entries      Remaining (unarranged) items, including all dependent (extracted) methods.
   * @param ruleInstance Rule containing parents (callers) of potentially related methods
   */
  public static void rearrangeRelatedItems(List<ClassContentsEntry> entries,
                                           RuleInstance ruleInstance,
                                           RelatedMethodsSettings rms)
  {
    List<RangeEntry> parentEntries = new ArrayList<RangeEntry>(ruleInstance.getMatches());
    for (RangeEntry o : parentEntries) {
      if (o instanceof RelatableEntry) {
        MethodEntry me = (MethodEntry)o;
        if (me.isGetter()) {
          if (me.myKeptWithProperty) {
            if (me.getMatchedRule() != null && me.getMatchedRule().getMatches() != null) {
              // prevent the getter from appearing under a rule it matches; it will be placed under the property
              me.getMatchedRule().getMatches().remove(me);
            }
          }
          for (MethodEntry theSetter : me.myCorrespondingGetterSetters) {
            final RuleInstance theRule = theSetter.getMatchedRule();
            LOG.debug(
              "rearrangeRelatedItems: for getter method " +
              me +
              ", corresponding setter is " +
              theSetter
            );
            if (theRule != null &&
                theRule.getMatches() != null)
            {
              LOG.debug(
                "remove entry " +
                theSetter.myEnd +
                " from matched rule" +
                theRule +
                "; matches = " +
                ((java.util.List)theRule.getMatches()) == null ? "null" : ""
              );
              theRule.getMatches().remove(theSetter);
            }
          }
        }
        if (me.myCalledMethods.size() > 0) {
          List<MethodEntry> parents = new LinkedList<MethodEntry>();
          parents.add(me);
          moveRelatedItems(
            entries,
            parents,
            rms,
            ((PsiMethod)me.myEnd).getName(),
            ((PsiMethod)me.myEnd).getName() + "()",
            1
          );
          if (LOG.isDebugEnabled()) {
            // dump sorted children recursively
            me.dumpChild(0);
          }
          me.assignComments(rms);
        }
      }
    }
  }

  /**
   * Move methods related to this one underneath it (beginning at the index specified.)  Do so in depth-first or
   * breadth-first order, as configured.  At each level, do alphabetical, original order, or call order sorting.
   *
   * @param entries list of global (all rule) unmatched items including dependent methods
   * @param parents contains a list of items to be handled; order is depth or breadth-first.
   */
  private static void moveRelatedItems(List<ClassContentsEntry> entries,
                                       List<MethodEntry> parents,
                                       RelatedMethodsSettings rms,
                                       String topLevelMethodName,
                                       String allMethodNames,
                                       int level)
  {
    allMethodNames = buildAllMethodNamesString(allMethodNames, parents);
    /**
     * First, identify all dependent methods.  Move them to parents in the specified order.
     */
    List<MethodEntry> children = ((MethodEntry)parents.get(parents.size() - 1)).sortedMethods;
//        if (rms.isDepthFirstOrdering())
//        {
    switch (rms.getOrdering()) {
      case RelatedMethodsSettings.RETAIN_ORIGINAL_ORDER: {
        /**
         * iterate through entries looking for those that are called by method(s) in the set of parent methods.
         * Add these to the list of children in order seen in entries.
         */
        ListIterator li = entries.listIterator();
        while (li.hasNext()) {
          Object o = li.next();
          if (o instanceof RelatableEntry) {
            MethodEntry me = (MethodEntry)o;
            for (MethodEntry entry : parents) {
              if (me.myCalledByMethods.contains(entry)) {
                children.add(me);
                li.remove();
                break;
              }
            }
          }
        }
      }
      break;
      case RelatedMethodsSettings.ALPHABETICAL_ORDER: {
        /**
         * iterate through entries looking for those that are called by method(s) in the set of parent methods.
         * Add these to the list of children alphabetically.
         */
        ListIterator li = entries.listIterator();
        while (li.hasNext()) {
          Object o = li.next();
          if (o instanceof RelatableEntry) {
            MethodEntry me = (MethodEntry)o;
            for (MethodEntry entry : parents) {
              if (me.myCalledByMethods.contains(entry)) {
                me.insertAlphabetically(children);
                li.remove();
                break;
              }
            }
          }
        }
      }
      break;
      case RelatedMethodsSettings.INVOCATION_ORDER:
        /**
         * iterate through calling methods, looking for remaining unmatched methods (in 'entries' list).
         * Add these to the list of children in order of invocation.
         */
        for (MethodEntry me : parents) {
          for (MethodEntry child : me.myCalledMethods) {
            if (entries.contains(child)) {
              children.add(child);
              entries.remove(child);
            }
          }
        }
        break;
    }
    /**
     * now children contains all the children of the parents for this level, and all these children have been
     * removed from "entries".  Move these to the rearranged list.  Then:
     * If depth-first, recurse setting the parent list to each of the children in turn.
     * If breadth first, recurse setting the parent list to all of the children.
     */
    if (children.size() > 0) {
      if (rms.isDepthFirstOrdering()) {
        for (MethodEntry entry : children) {
          if (entry.myCalledMethods.size() == 0) {
            continue;
          }
          List<MethodEntry> parent = new LinkedList<MethodEntry>();
          parent.add(entry);
          moveRelatedItems(
            entries,
            parent,
            rms,
            topLevelMethodName,
            allMethodNames + "." + ((PsiMethod)entry.myEnd).getName() + "()",
            level + 1
          );
        }
      }
      else {
        moveRelatedItems(
          entries,
          children,
          rms,
          topLevelMethodName,
          allMethodNames + ".Depth " + level,
          level + 1
        );
      }
    }
  }

  private static String buildAllMethodNamesString(String allMethodNames, List<MethodEntry> parents) {
    StringBuffer allMN = new StringBuffer(120);
    allMN.append(allMethodNames);
    if (allMN.length() > 0) {
      allMN.append(".");
    }
    if (parents.size() > 1) {
      allMN.append("[");
    }
    {
      boolean first = true;
      for (MethodEntry entry : parents) {
        if (!first) {
          allMN.append(",");
        }
        first = false;
        allMN.append(((PsiMethod)entry.myEnd).getName());
      }
    }
    if (parents.size() > 1) {
      allMN.append("]");
    }
    return allMN.toString();
  }

  public void insertAlphabetically(final List<MethodEntry> list) {
    Comparator<MethodEntry> comparator = new Comparator<MethodEntry>() {
      public int compare(MethodEntry me, MethodEntry me2) {
        String s = ((PsiMethod)me.myEnd).getName();
        if (me.isGetter()) {
          s = MethodUtil.getPropertyName((PsiMethod)me.myEnd);
        }
        return s.compareTo(((PsiMethod)me2.myEnd).getName());
      }
    };
    insertInList(list, comparator);
  }

  void insertInList(final List<MethodEntry> list, Comparator<MethodEntry> comparator) {
    boolean inserted = false;
    ListIterator li = list.listIterator();
    while (li.hasNext() && !inserted) {
      MethodEntry entry = ((MethodEntry)li.next());
      if (comparator.compare(this, entry) < 0) {
        LOG.debug(
          "insertInList dependent method: add " + myEnd.toString() + " at index " + (li.nextIndex() - 1)
        );
        list.add(li.nextIndex() - 1, this);
        inserted = true;
      }
    }
    if (!inserted) {
      list.add(this);
    }
  }

  void dumpChild(int level) {
    LOG.debug(level + ": " + ((PsiMethod)myEnd).getName());
    for (MethodEntry methodEntry : sortedMethods) {
      methodEntry.dumpChild(level + 1);
    }
  }

  private void assignComments(RelatedMethodsSettings rms) {
    List<MethodEntry> callingMethod = new ArrayList<MethodEntry>();
    callingMethod.add(this);
    assignComments(rms, callingMethod, "", 1);
  }

  /**
   * Eliminate cycles of method calls so that the related method tree is a tree, not a cycle (or directed graph).
   * If method A calls method B, B calls C, and C calls A, eliminate the final call to prevent cycling.
   *
   * @param contents
   */
  public static void eliminateCycles(List<ClassContentsEntry> contents) {
    for (ClassContentsEntry entry : contents) {
      if (entry instanceof RelatableEntry) {
        MethodEntry current = (MethodEntry)entry;
        List<MethodEntry> set = new LinkedList<MethodEntry>();
        set.add(current);
        test(current, set);
      }
    }
  }

  private static void test(MethodEntry current, List<MethodEntry> set) {
    Iterator<MethodEntry> it = current.myCalledMethods.iterator();
    while (it.hasNext()) {
      MethodEntry callee = it.next();
      if (set.contains(callee)) {
        callee.myCalledByMethods.remove(current);
        if (callee.myCalledByMethods.size() == 0) {
          callee.myRelatedMethod = false;
        }
        it.remove();
      }
      else {
        set.add(callee);
        test(callee, set);
      }
    }
  }

  /**
   * If setting indicates, move all overloaded methods (of the same name)
   * adjacent with the first encountered. Sort in the configured order
   * (original order, or by number of parameters).
   */
  public static void handleOverloadedMethods(List<ClassContentsEntry> contents,
                                             RearrangerSettings settings)
  {
    if (!settings.isKeepOverloadedMethodsTogether()) return;
    /**
     * first, detect overloaded methods and move them to the first method's list of overloaded methods.
     * Make two passes, first for extracted methods, then for non-extracted methods.  This will organize any
     * overloaded methods for extracted methods with them (as opposed to whichever was seen first.)
     */
    cullOverloadedMethods(contents, true);
    LOG.debug("entered handleOverloadedMethods(): move overloaded methods together");
    cullOverloadedMethods(contents, false);
    List<ClassContentsEntry> copy = new ArrayList<ClassContentsEntry>(contents);
    for (ClassContentsEntry rangeEntry : copy) {
      if (rangeEntry instanceof RelatableEntry) {
        MethodEntry current = (MethodEntry)rangeEntry;
        if (current.myOverloadedMethods.size() > 0) {
          List<MethodEntry> newList = new ArrayList<MethodEntry>(current.myOverloadedMethods.size() + 1);
          newList.add(current);
          /**
           * we are looking at the head of a list of overloaded methods.  We need to sort the list
           * if necessary and, if the head of the list has changed, replace it in the contents array.
           */
          switch (settings.getOverloadedOrder()) {
            case RearrangerSettings.OVERLOADED_ORDER_RETAIN_ORIGINAL:
              // list is already in original order, except perhaps that the top-most extracted method
              // comes first (if there is one).
              newList.addAll(current.myOverloadedMethods);
              break;
            case RearrangerSettings.OVERLOADED_ORDER_ASCENDING_PARAMETERS:
            case RearrangerSettings.OVERLOADED_ORDER_DESCENDING_PARAMETERS:
              for (MethodEntry entry : current.myOverloadedMethods) {
                boolean inserted = false;
                for (int index = 0; index < newList.size(); index++) {
                  MethodEntry me = newList.get(index);
                  if (settings.getOverloadedOrder() == RearrangerSettings.OVERLOADED_ORDER_ASCENDING_PARAMETERS
                      ? me.getnParameters() > entry.getnParameters()
                      : me.getnParameters() < entry.getnParameters())
                  {
                    newList.add(index, entry);
                    inserted = true;
                    break;
                  }
                }
                if (!inserted) {
                  newList.add(entry);
                }
              }
              break;
          }
          current.myOverloadedMethods.clear();
          /**
           * if the head of the arraylist is not the same as "current", then the sort operation moved
           * another method to the head of the list.  Replace that in the contents array.  Then assign
           * a new ordered list.
           */
          int index = contents.indexOf(current);
          if (newList.get(0) != current) {
            contents.set(index, ((MethodEntry)newList.get(0)));
          }
          /**
           * insert the remaining overloaded methods after the current entry.
           */
          newList.remove(0);
          for (int j = 0; j < newList.size(); j++) {
            contents.add(index + j + 1, ((MethodEntry)newList.get(j)));
          }
        }
      }
    }
  }

  /**
   * Removes overloaded methods from the contents list and adds them to the overloadedMethods list of the first
   * overloaded method encountered.
   *
   * @param contents
   * @param doExtractedMethods
   */
  private static void cullOverloadedMethods(List<ClassContentsEntry> contents,
                                            boolean doExtractedMethods)
  {
    List<ClassContentsEntry> copy = new ArrayList<ClassContentsEntry>(contents);
    for (ClassContentsEntry o : copy) {
      if (o instanceof RelatableEntry) {
        MethodEntry me = (MethodEntry)o;
        if ((me.isRelatedMethod() == doExtractedMethods) && !me.isOverloadedMethod) {
          String meName = me.myEnd.toString();
          // search contents list for methods with identical name, and attach them as overloaded methods.
          ListIterator<ClassContentsEntry> contentIterator = contents.listIterator();
          while (contentIterator.hasNext()) {
            Object o1 = contentIterator.next();
            if (o1 instanceof RelatableEntry) {
              MethodEntry me2 = (MethodEntry)o1;
              if (me2 == me) {
                continue;
              }
              String me2Name = me2.myEnd.toString();
              if (meName.equals(me2Name)) {
                contentIterator.remove();
                me.myOverloadedMethods.add(me2);
                me2.isOverloadedMethod = true; // set flag so array copy will skip this entry.
              }
            }
          }
        }
      }
    }
  }

  public int getnParameters() {
    return nParameters;
  }

// --------------------------- CONSTRUCTORS ---------------------------

  public MethodEntry(final PsiElement start,
                     final PsiElement end,
                     final int modifiers,
                     final String modifierString,
                     final String name,
                     final String type,
                     int nParameters,
                     final String interfaceName)
  {
    super(start, end, modifiers, modifierString, name, type);
    this.nParameters = nParameters;
    this.interfaceName = interfaceName;
  }

// --------------------- GETTER / SETTER METHODS ---------------------

  public String[] getAdditionalIconNames() {
    ArrayList<String> result = new ArrayList<String>();
    String[] sa = new String[0];
    if (myEnd instanceof PsiMethod) {
      PsiMethod m = (PsiMethod)myEnd;
      if (m.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
        result.add("nodes/c_public");
      }
      else if (m.getModifierList().hasModifierProperty(PsiModifier.PROTECTED)) {
        result.add("nodes/c_protected");
      }
      else if (m.getModifierList().hasModifierProperty(PsiModifier.PRIVATE)) {
        result.add("nodes/c_private");
      }
      else {
        result.add("nodes/c_plocal");
      }
      if ((getModifiers() & ModifierConstants.IMPLEMENTING) != 0) {
        result.add("gutter/implementingMethod");
      }
      if ((getModifiers() & ModifierConstants.IMPLEMENTED) != 0) {
        result.add("gutter/implementedMethod");
      }
      if ((getModifiers() & ModifierConstants.OVERRIDING) != 0) {
        result.add("gutter/overridingMethod");
      }
      if ((getModifiers() & ModifierConstants.OVERRIDDEN) != 0) {
        result.add("gutter/overridenMethod");
      }
    }
    return result.toArray(sa);
  }

  public String getTypeIconName() {
    if (myEnd instanceof PsiMethod) {
      PsiMethod m = (PsiMethod)myEnd;
      return (((PsiModifierList)m.getModifierList()).hasModifierProperty(PsiModifier.STATIC)) ? "nodes/staticMethod" : "nodes/method";
    }
    return null;
  }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface IFilePopupEntry ---------------------

  public JLabel getPopupEntryText(RearrangerSettings settings) {
    StringBuffer name = new StringBuffer(80);

    PsiMethod m = (PsiMethod)myEnd;
    if (m.getReturnTypeElement() != null && !settings.isShowTypeAfterMethod()) {
      name.append(m.getReturnTypeElement().getText());
      name.append(' ');
    }
    name.append(m.getName());
    PsiParameterList plist = m.getParameterList();
    if (settings.isShowParameterNames() ||
        settings.isShowParameterTypes())
    {
      if (plist != null) {
        name.append("(");
        for (int i = 0; i < plist.getParameters().length; i++) {
          if (i > 0) {
            name.append(", ");
          }
          if (settings.isShowParameterTypes()) {
            String modifiers = plist.getParameters()[i].getModifierList().getText();
            if (modifiers.length() > 0) {
              name.append(modifiers);
              name.append(' ');
            }
            name.append(plist.getParameters()[i].getTypeElement().getText());
            if (settings.isShowParameterNames()) {
              name.append(' ');
            }
          }
          if (settings.isShowParameterNames()) {
            name.append(plist.getParameters()[i].getName());
          }
        }
        name.append(")");
      }
    }
    if (m.getReturnTypeElement() != null && settings.isShowTypeAfterMethod()) {
      name.append(": ");
      name.append(m.getReturnTypeElement().getText());
    }
    return new JLabel(name.toString());
  }

// --------------------- Interface IRelatableEntry ---------------------

  public void determineGetterSetterAndExtractedMethodStatus(RearrangerSettings settings) {
    LOG.debug("building method call & getter-setter graph for " + getName());
    /**
     * check all rules to find first that this method matches.  If that rule specifies
     * that matching methods are excluded from extracted method treatment, then skip this
     * step.  If this method is a getter (according to that rule's definition) then
     * determine its setter (according to that rule's definition) at this point also.
     */
    setNoExtractedMethods(false);
    /**
     * The following code determines whether the method is a getter/setter based on default definition.
     * This is necessary in case there are no rules, but "Keep Getters/Setters Together" is set.
     * If the method matches an individual rule with its own getter/setter definition, the values of
     * getter and setter will be changed to match that rule's definition.
     */
    setGetter(
      MethodUtil.isGetter(
        (PsiMethod)myEnd,
        settings.getDefaultGSDefinition()
      )
    );
    setSetter(
      MethodUtil.isSetter(
        (PsiMethod)myEnd,
        settings.getDefaultGSDefinition()
      )
    );
    for (Rule rule : settings.getItemOrderAttributeList()) {
      if (rule instanceof IRestrictMethodExtraction) {
        if (rule.isMatch(this)) {
          if (((IRestrictMethodExtraction)rule).isNoExtractedMethods()) {
            LOG.debug(
              "excluding " +
              myEnd.toString() +
              " from extracted method consideration"
            );
            setNoExtractedMethods(true);
          }
          setGetter(false);
          if (rule instanceof IHasGetterSetterDefinition) {
            if (MethodUtil.isGetter(
              (PsiMethod)getEnd(),
              ((IHasGetterSetterDefinition)rule).getGetterSetterDefinition()
            ))
            {
              if (settings.isKeepGettersSettersTogether()) {
                setGetter(true);
              }
            }
            setSetter(
              MethodUtil.isSetter(
                (PsiMethod)getEnd(),
                ((IHasGetterSetterDefinition)rule).getGetterSetterDefinition()
              )
            );
          }
          break;
        }
      }
    }
  }

  public void determineSettersAndMethodCalls(RearrangerSettings settings, List<ClassContentsEntry> contents) {
    if (isGetter()) {
      if (settings.isKeepGettersSettersTogether()) {
        determineSetter(contents, settings); // link getters/setters via correspondingGetterSetter entries
      }
    }
    if (!isNoExtractedMethods() &&
        settings.getExtractedMethodsSettings().isMoveExtractedMethods())
    {
      determineMethodCalls(contents, settings);
    }
  }

  /**
   * Decide if a method is an extracted method.  It is an extracted method if:
   * - it is called by at least one other method, and
   * - it is private, or passes the "never / 1 / >1" caller test.
   * <p/>
   * (It is not considered an extracted method if it is a setter, there is a corresponding getter, and the
   * option to keep getters and setters together is set.  This is a special case.)
   */
  public void determineExtractedMethod(RelatedMethodsSettings settings) {
    myRelatedMethod = false;
    if (!isGetter() && !isSetter()) {
      if (myCalledByMethods.size() > 0) {
        PsiMethod m = (PsiMethod)myEnd;
        if (!m.getModifierList().hasModifierProperty(PsiModifier.PRIVATE)) {
          switch (settings.getNonPrivateTreatment()) {
            case RelatedMethodsSettings.NON_PRIVATE_EXTRACTED_NEVER:
              break;
            case RelatedMethodsSettings.NON_PRIVATE_EXTRACTED_ONE_CALLER:
              if (myCalledByMethods.size() == 1) {
                myRelatedMethod = true;
              }
              break;
            case RelatedMethodsSettings.NON_PRIVATE_EXTRACTED_ANY_CALLERS:
              myRelatedMethod = true;
              break;
          }
        }
        else {
          myRelatedMethod = true;
        }
      }
    }
    else {
      if (isSetter() && myCalledByMethods.size() > 0 && myCorrespondingGetterSetters.size() == 0) {
        myRelatedMethod = true;
        LOG.debug(myEnd.toString() + " is setter but has no getter, treated as extracted method");
      }
      else {
        LOG.debug(myEnd.toString() + " is getter/setter, not treated as extracted method");
      }
    }
    LOG.debug("determined " + myEnd.toString() + " is extracted method? " + myRelatedMethod);
    /**
     * If this is not an extracted method, remove it from any callers so that it won't be moved.
     */
    if (!myRelatedMethod) {
      ListIterator<MethodEntry> li = myCalledByMethods.listIterator();
      while (li.hasNext()) {
        MethodEntry entry = (li.next());
        entry.myCalledMethods.remove(this);
        li.remove();
      }
    }
    /**
     * Remaining entries are only extracted methods at this point.  Using first/last rule, keep only the call by
     * the calling method that this child method will be grouped with, and discard the rest.
     */
    if (myCalledByMethods.size() > 1) {
      ListIterator<MethodEntry> li;
      if (settings.isBelowFirstCaller()) {
        li = myCalledByMethods.listIterator(1);
        while (li.hasNext()) {
          MethodEntry entry = (li.next());
          entry.myCalledMethods.remove(this);
          li.remove();
        }
      }
      else {
        li = myCalledByMethods.listIterator(myCalledByMethods.size() - 1);
        while (li.hasPrevious()) {
          MethodEntry entry = (li.previous());
          entry.myCalledMethods.remove(this);
          li.remove();
        }
      }
    }
    if (myRelatedMethod) {
      LOG.debug(
        "extracted method " +
        toString() +
        " will be arranged under " +
        myCalledByMethods.get(0).toString()
      );
    }
  }

// -------------------------- OTHER METHODS --------------------------

  public DefaultMutableTreeNode addToPopupTree(DefaultMutableTreeNode parent, RearrangerSettings settings) {
    DefaultMutableTreeNode node = new RearrangerTreeNode(this, myName);
    parent.add(node);
    ListIterator li;
    for (MethodEntry methodEntry : sortedMethods) {
      if (methodEntry.isSetter() && methodEntry.myCalledByMethods.size() > 0) {
        // setters are arranged with getters when "keep getters/setters together" option is checked.
        // but setters are not really called by getters.  So attach them to the upper level.
        methodEntry.addToPopupTree(parent, settings);
      }
      else {
        methodEntry.addToPopupTree(node, settings);
      }
    }
    for (MethodEntry methodEntry : myOverloadedMethods) {
      methodEntry.addToPopupTree(node, settings);
    }
    return node;
  }

  /**
   * Set preceding and/or trailing comments on the appropriate methods, based on the comment type.
   *
   * @param rms
   * @param allMethodNames contains a list of methods invoked to reach this point.  Top level is empty string.
   * @param level          current nesting level; top level is 1.
   */
  private void assignComments(RelatedMethodsSettings rms,
                              List<MethodEntry> allCallingMethods,
                              String allMethodNames,
                              int level)
  {
    final String currentMethodName = ((PsiMethod)myEnd).getName();
    String[] callingNames = new String[allCallingMethods.size()];
    for (int i = 0; i < allCallingMethods.size(); i++) {
      MethodEntry me = allCallingMethods.get(i);
      callingNames[i] = ((PsiMethod)me.myEnd).getName();
    }
    MethodEntry topLevel = this;
    while (topLevel.myCalledByMethods.size() > 0) {
      topLevel = (topLevel.myCalledByMethods.get(0));
    }
    switch (rms.getCommentType()) {
      case RelatedMethodsSettings.COMMENT_TYPE_TOP_LEVEL:
        customizedPrecedingComment = expandComment(
          rms.getPrecedingComment(),
          currentMethodName,
          allMethodNames,
          currentMethodName,
          level
        );
        MethodEntry last = this;
        while (last.sortedMethods.size() > 0) {
          last = (last.sortedMethods.get(last.sortedMethods.size() - 1));
        }
        last.customizedTrailingComment = expandComment(
          rms.getTrailingComment(),
          currentMethodName,
          allMethodNames,
          currentMethodName,
          level
        );
        break;
      case RelatedMethodsSettings.COMMENT_TYPE_EACH_METHOD:
        customizedPrecedingComment = expandComment(
          rms.getPrecedingComment(),
          currentMethodName,
          allMethodNames,
          ((PsiMethod)topLevel.myEnd).getName(),
          level
        );
        customizedTrailingComment = expandComment(
          rms.getTrailingComment(),
          currentMethodName,
          allMethodNames,
          ((PsiMethod)topLevel.myEnd).getName(),
          level
        );
        // recursively assign comments.
        for (MethodEntry methodEntry : sortedMethods) {
          String newAllMethodNames = appendMN(
            allMethodNames,
            callingNames,
            allCallingMethods,
            this,
            rms
          );
          methodEntry.assignComments(rms, sortedMethods, newAllMethodNames, level + 1);
        }
        break;
      case RelatedMethodsSettings.COMMENT_TYPE_NEW_FAMILY: {
        /**
         * Specialized routine to assign comments to "families" of
         * methods. Insert a preceding comment before every sibling
         * except: when it is the first, and when the prior sibling
         * had no children and this sibling has no children.
         */
        MethodEntry firstEntry = null;
        MethodEntry previousEntry = null;
        for (MethodEntry methodEntry : allCallingMethods) {
          if (firstEntry == null) {
            firstEntry = methodEntry;
          }
          else if (methodEntry.sortedMethods.size() != 0 ||
                   previousEntry.sortedMethods.size() != 0)
          {
            methodEntry.customizedPrecedingComment =
              expandComment(
                rms.getPrecedingComment(),
                currentMethodName,
                allMethodNames,
                ((PsiMethod)topLevel.myEnd).getName(),
                level
              );
          }
          previousEntry = methodEntry;
          if (methodEntry.sortedMethods.size() != 0) {
            String newAllMethodNames = appendMN(
              allMethodNames,
              callingNames,
              allCallingMethods,
              methodEntry,
              rms
            );
            methodEntry.assignComments(rms, methodEntry.sortedMethods, newAllMethodNames, level + 1);
          }
        }
      }
      break;
      case RelatedMethodsSettings.COMMENT_TYPE_EACH_LEVEL: {
        MethodEntry beginLevel = allCallingMethods.get(0);
        MethodEntry endLevel = allCallingMethods.get(allCallingMethods.size() - 1);
        beginLevel.customizedPrecedingComment = expandComment(
          rms.getPrecedingComment(),
          currentMethodName,
          allMethodNames,
          ((PsiMethod)topLevel.myEnd).getName(),
          level
        );
        // recursively assign comments for each level.
        for (MethodEntry methodEntry : allCallingMethods) {
          if (methodEntry.sortedMethods.size() > 0) {
            String newAllMethodNames = appendMN(
              allMethodNames,
              callingNames,
              allCallingMethods,
              methodEntry,
              rms
            );
            methodEntry.assignComments(rms, methodEntry.sortedMethods, newAllMethodNames, level + 1);
          }
        }
        while (endLevel.sortedMethods.size() > 0) {
          endLevel = endLevel.sortedMethods.get(endLevel.sortedMethods.size() - 1);
        }
        if (endLevel.customizedTrailingComment.length() > 0) {
          endLevel.customizedTrailingComment += "\n";
        }
        endLevel.customizedTrailingComment += expandComment(
          rms.getTrailingComment(),
          currentMethodName,
          allMethodNames,
          ((PsiMethod)topLevel.myEnd).getName(),
          level
        );
      }
      break;
    }
  }

  /**
   * Build a string representing a sequence of method calls needed to reach a particular child method.
   *
   * @param allMethods     previous method call sequence
   * @param newMethods     names of each of the methods in callingMethods
   * @param callingMethods MethodEntry objects for each method
   * @param currentMethod  current method being handled
   * @param rms            contains setting determining if depth-first or breadth-first traversal has taken place.
   * @return
   */
  private String appendMN(String allMethods,
                          String[] newMethods,
                          List callingMethods,
                          MethodEntry currentMethod,
                          RelatedMethodsSettings rms)
  {
    StringBuffer result = new StringBuffer(allMethods.length() + 80);
    result.append(allMethods);
    if (rms.isDepthFirstOrdering()) {
      if (newMethods.length > 0) {
        if (result.length() > 0) result.append('.');
      }
      int index = callingMethods.indexOf(currentMethod);
      result.append(newMethods[index]);
      result.append("()");
    }
    else {
      if (newMethods.length > 0) {
        if (result.length() > 0) result.append('.');
        if (newMethods.length > 1) {
          result.append('[');
        }
        for (int i = 0; i < newMethods.length; i++) {
          if (i > 0) result.append(',');
          result.append(newMethods[i]);
          result.append("()");
        }
        if (newMethods.length > 1) {
          result.append(']');
        }
      }
    }
    return result.toString();
  }

  private static String expandComment(CommentRule comment,
                                      String methodName,
                                      String methodNames,
                                      String topLevelMethodName,
                                      int level)
  {
    if (comment == null) return "";
    String result = comment.getCommentText();
    if (result == null) return "";
    result = result.replaceAll("%TL%", topLevelMethodName);
    result = result.replaceAll("%MN%", methodName);
    result = result.replaceAll("%AM%", methodNames);
    result = result.replaceAll("%LV%", String.valueOf(level));
    return result;
  }

  public void checkForComment() {
    if (CommentUtil.getCommentMatchers().size() == 0) {
      return;
    }
    createAlternateValueString();
    // We don't want to check for comments in the body of the method.  So reduce the alternate value string
    // to only that text up to and including the open brace.  Temporarily remove the rest of the text; append
    // it again after checking for comments.
    int brace = myAlternateValue.indexOf('{');
    String temp = "";
    if (brace >= 0) {
      // every method should have an open brace -- unless it is abstract.
      temp = myAlternateValue.substring(brace + 1, myAlternateValue.length());
      myAlternateValue = myAlternateValue.substring(0, brace + 1);
    }
    super.checkForComment();
    myAlternateValue += temp;
  }

  /**
   * Analyzes the method calls made by this method and creates a "call graph," linking caller and callee by
   * references in the calledMethods and calledByMethods lists, respectively.  Only method calls to methods in
   * this class are entered into the call graph, because only methods in this class can be moved underneath
   * their caller.
   *
   * @param possibleMethods list of methods in this class; call graph is limited to these methods
   * @param settings        current configuration
   */
  public void determineMethodCalls(final List<ClassContentsEntry> possibleMethods,
                                   final RearrangerSettings settings)
  {
    /**
     * recursively walk the method's code block looking for method calls.
     */
    final PsiMethod thisMethod = (PsiMethod)myEnd;
    final MethodEntry thisMethodEntry = this;

    JavaRecursiveElementVisitor rev = new JavaRecursiveElementVisitor() {
      public void visitMethodCallExpression(PsiMethodCallExpression psiMethodCallExpression) {
//                logger.debug("visitMethodCallExpression:" + psiMethodCallExpression.toString());
        PsiElement c = psiMethodCallExpression.getMethodExpression().getReference().resolve();
        /**
         * if the called method is already in our list, don't add it again.
         */
        if (c != null && !myCalledMethods.contains(c)) {
          for (ClassContentsEntry o : possibleMethods) {
            if (o instanceof RelatableEntry) {
              MethodEntry me = (MethodEntry)o;
              PsiMethod m = (PsiMethod)me.myEnd;
              if (c == m) {
                if (settings.isKeepOverloadedMethodsTogether() &&
                    m.getName().equals(thisMethod.getName()))
                {
                  LOG.debug("method " + me + " is overload, not inserted in call graph");
                }
                else {
                  LOG.debug("method " + thisMethod.toString() + " calls " + m.toString());
                  myCalledMethods.add(me);
                  me.myCalledByMethods.add(thisMethodEntry);
                }
                break;
              }
            }
          }
        }
        // now parse the expression list; it also may contain method calls.
        super.visitExpressionList(psiMethodCallExpression.getArgumentList());
      }
    };

    thisMethod.accept(rev);
  }

  /**
   * Called when getters and setters are to be kept together in pairs.  Searches the list of entries for
   * any getter/setter methods that matches this getter method.  Normally there is only one setter per getter,
   * but one could have a "getXXX", "isXXX" and "setXXX" trio which need to be related.  In this case, the first
   * getter encountered finds the other getter and setter and the three will be emitted in that order.
   *
   * @param possibleMethods entry list, possibly containing the corresponding setter for this getter.
   * @param settings
   */
  public void determineSetter(final List<ClassContentsEntry> possibleMethods, RearrangerSettings settings) {
    if (!isGetter())      // wasted assertion, already checked before calling
    {
      return;
    }
    final PsiMethod thisMethod = (PsiMethod)myEnd;
    String thisProperty = MethodUtil.getPropertyName(thisMethod);
    if (isGetter() && !myKeptWithProperty) {
      if (settings.isKeepGettersSettersWithProperty()) {
        hookGetterToProperty(possibleMethods);
      }
    }
    for (ClassContentsEntry o : possibleMethods) {
      if (o instanceof RelatableEntry) {
        MethodEntry me = (MethodEntry)o;
        // don't use a setter twice (could be two methods which both look like getters; assign the setter
        // to only one of them.)  Also, associate all getter/setter methods for the same property with the
        // first one encountered.
        if ((me.isSetter() ||
             me.isGetter()) &&
            me.myCorrespondingGetterSetters.size() == 0 &&
            me != this)
        {
          PsiMethod m = (PsiMethod)me.myEnd;
          String otherProperty = MethodUtil.getPropertyName(m);
          if (thisProperty.equals(otherProperty)) {
            LOG.debug("method " + thisMethod.toString() + " is getter; its setter is " + m.toString());
            // place getters ahead of setters
            if (me.isGetter()) {
              myCorrespondingGetterSetters.add(0, me);
              // clear the getter flag and set the setter flag; this causes the method to be emitted
              // under the first getter encountered.
              me.setGetter(false);
              me.setSetter(true);
            }
            else {
              myCorrespondingGetterSetters.add(me);
            }
            me.myCorrespondingGetterSetters.add(this);
          }
        }
      }
    }
  }

  /**
   * Getters/Setters are supposed to be kept with their associated property.  Search the list of entries to find
   * the property and attach the setter.
   *
   * @param entries list of all items (methods, fields) in the class.
   */
  private void hookGetterToProperty(List<ClassContentsEntry> entries) {
    ListIterator<ClassContentsEntry> li = entries.listIterator();
    String property = MethodUtil.getPropertyName((PsiMethod)myEnd);
    while (li.hasNext()) {
      Object o = li.next();
      if (o instanceof FieldEntry) {
        FieldEntry fe = (FieldEntry)o;
        StringBuffer sb = new StringBuffer(fe.getName());
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        if (fe.getGetterMethod() == null && property.equals(sb.toString())) {
          fe.setGetterMethod(this);
          myKeptWithProperty = true;
          break;
        }
      }
    }
  }

  public void emit(Emitter emitter) {
    StringBuilder sb = emitter.getTextBuffer();
    if (getCustomizedPrecedingComment().length() > 0) {
      sb.append("\n");
      sb.append(getCustomizedPrecedingComment());
    }
    emitAllElements(sb, emitter.getDocument());
    if (getCustomizedTrailingComment().length() > 0) {
      sb.append("\n");
      sb.append(getCustomizedTrailingComment());
    }
    /**
     * emit corresponding setter, overloaded methods, and related methods, if any.
     */
    ListIterator li;
    if (isGetter()) {
      for (MethodEntry entry : myCorrespondingGetterSetters) {
        entry.emit(emitter);
      }
    }
    for (MethodEntry me : myOverloadedMethods) {
      me.emit(emitter);
    }
    for (MethodEntry me : sortedMethods) {
      me.emit(emitter);
    }
  }

  protected void emitAllElements(StringBuilder sb, Document document) {
    if (myAlternateValue != null) {
      /**
       * protect body of method from removing newlines.
       */
      int brace = myAlternateValue.indexOf('{');
      String temp = "";
      if (brace >= 0) {
        // every method should have an open brace -- unless it is abstract.
        temp = myAlternateValue.substring(brace + 1, myAlternateValue.length());
        myAlternateValue = myAlternateValue.substring(0, brace + 1);
      }
      super.emitAllElements(sb, document);
      sb.append(temp);
    }
    else {
      super.emitAllElements(sb, document);
    }
  }

  public void insertInterfaceOrder(List<MethodEntry> list) {
    Comparator<MethodEntry> comparator = new Comparator<MethodEntry>() {
      public int compare(MethodEntry o1, MethodEntry o2) {
        final int offset1 = getMethodOffsetInInterface((PsiMethod)o1.myEnd);
        final int offset2 = getMethodOffsetInInterface((PsiMethod)o2.myEnd);
        if (offset1 < offset2) return -1;
        if (offset1 == offset2) return 0;
        return 1;
      }
    };
    insertInList(list, comparator);
  }

  private static int getMethodOffsetInInterface(PsiMethod method) {
    final PsiMethod[] superMethods = method.findSuperMethods();
//        final PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(method); // todo - for IDEA 5.0
    if (superMethods.length == 0) return 0;
    PsiMethod m = superMethods[0];
    PsiElement parent = m.getParent();
    if (parent instanceof PsiClass && ((PsiClass)parent).isInterface()) {
      return m.getTextOffset();
    }
    return getMethodOffsetInInterface(m);
  }

  public String toString() {
    return "MethodEntry " +
           myEnd.toString() +
           "; calls " +
           myCalledMethods.size() +
           ", called by " +
           myCalledByMethods.size() +
           ", nParameters=" + nParameters;
  }
}

