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
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.util.Query;
import com.wrq.rearranger.ModifierConstants;
import com.wrq.rearranger.popup.FilePopupEntry;
import com.wrq.rearranger.rearrangement.Emitter;
import com.wrq.rearranger.rearrangement.GenericRearranger;
import com.wrq.rearranger.ruleinstance.RuleInstance;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.util.ModifierUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Describes an entire class's range and type.
 * This information is used when reordering outer classes.
 */
public class ClassEntry extends ClassContentsEntry implements FilePopupEntry {

  private static final Logger LOG = Logger.getInstance("#" + ClassEntry.class.getName());

  protected final List<ClassContentsEntry> myContents;
  private         List<RuleInstance>       myResultRuleInstances;
  private final   RearrangerSettings       mySettings;
  private final   int                      myNestingLevel;

  public ClassEntry(@Nullable PsiElement start,
                    @Nullable PsiElement end,
                    int modifiers,
                    String modifierString,
                    @Nullable String name,
                    int nestingLevel,
                    RearrangerSettings settings) {
    super(start, end, modifiers, modifierString, name, "");
    myContents = new ArrayList<ClassContentsEntry>();
    myResultRuleInstances = null;
    mySettings = settings;
    myNestingLevel = nestingLevel;
  }

  public String getTypeIconName() {
    String result = "nodes/class";
    if (myEnd.getParent() instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)myEnd.getParent();
      if (psiClass.isEnum()) {
        result = "nodes/enum";
      }
      else if (psiClass.isInterface()) {
        result = "nodes/interface";
      }
    }
    return result;
  }

  public String[] getAdditionalIconNames() {
    if (myEnd instanceof PsiJavaToken && myEnd.getText().equals("{")) {
      PsiClass psiClass = (PsiClass)myEnd.getParent();
      final PsiModifierList modifierList = psiClass.getModifierList();
      if (modifierList == null) {
        return null;
      }
      
      if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
        return new String[]{"nodes/c_public"};
      }
      if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
        return new String[]{"nodes/c_protected"};
      }
      if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
        return new String[]{"nodes/c_private"};
      }
      return new String[]{"nodes/c_plocal"};
    }
    return null;
  }

  public JLabel getPopupEntryText(RearrangerSettings settings) {
    return new JLabel(myName);
  }

  protected void parseRemainingClassContents(@NotNull final Project project,
                                             int startingIndex,
                                             @NotNull final PsiElement psiClass
  ) {
    final PsiSearchHelper psh = PsiSearchHelper.SERVICE.getInstance(project);
    int lastIndex = startingIndex;
    /**
     * if option indicates, don't parse inner class contents; leave them unchanged.
     */
    if (mySettings.isRearrangeInnerClasses() || myNestingLevel <= 1) {
      for (int i = startingIndex; i < psiClass.getChildren().length; i++) {
        PsiElement child = psiClass.getChildren()[i];
        if (child instanceof PsiJavaToken && child.getText().equals("{")) {
          lastIndex = i + 1; // first item of a class starts immediately after the class left brace.
        }
        LOG.debug(psiClass.toString() + " child " + i + ":" + child.toString());
        if (child instanceof PsiField ||
            child instanceof PsiMethod ||
            child instanceof PsiClass ||
            child instanceof PsiClassInitializer)
        {
          MemberAttributes attributes = new MemberAttributes();
          PsiElement startElement = psiClass.getChildren()[lastIndex];

          ClassContentsEntry classContentsEntry = null;
          if (child instanceof PsiField) {
            i = parseField(child, attributes, i, (PsiClass)psiClass);
            // a field declaration with multiple fields like "int x, y;" must advance child to "y;"
            // to prevent splitting.
            child = psiClass.getChildren()[i];
            classContentsEntry = new FieldEntry(
              startElement,
              child,
              attributes.modifiers,
              attributes.modifierString,
              attributes.name,
              attributes.type
            );
          }
          if (child instanceof PsiMethod) {
            parseMethod(child, attributes, psh);
            classContentsEntry = new MethodEntry(
              startElement,
              child,
              attributes.modifiers,
              attributes.modifierString,
              attributes.name,
              attributes.type,
              attributes.nParameters,
              attributes.interfaceName
            );
          }
          if (child instanceof PsiClass) {
            parseClassAttributes((PsiClass)child, attributes);
            //
            // if child is an enum, set the last entry past the LBrace to the final enumeration or the
            // semicolon thereafter, if any.
            //
            PsiClass clazz = (PsiClass)child;
            PsiElement clazzEnd;
            int startElementIndex = 0;
            if (clazz.isEnum()) {
              // we want to skip all the enumeration fields, including terminating semicolon if present.
              // class parsing begins after these.
              startElementIndex = findLastEnumTerminator(clazz);
              clazzEnd = clazz.getChildren()[startElementIndex];
              startElementIndex++;
            }
            else {
              // normal class: parse everything after the left brace.
              clazzEnd = clazz.getLBrace();
              startElementIndex = Arrays.asList(clazz.getChildren()).indexOf(clazzEnd) + 1;
            }
            ClassEntry entry = new ClassEntry(
              startElement,
              clazzEnd,
              attributes.modifiers,
              attributes.modifierString,
              attributes.name,
              myNestingLevel + 1,
              mySettings
            );
            classContentsEntry = entry;
            entry.parseRemainingClassContents(
              project,
              startElementIndex,
              child);
          }
          if (child instanceof PsiClassInitializer) {
            parseClassInitializer(child, attributes);
            classContentsEntry = new ClassInitializerEntry(startElement,
                                                           child,
                                                           attributes.modifiers,
                                                           attributes.modifierString,
                                                           attributes.name);
          }
          myContents.add(classContentsEntry);
          classContentsEntry.checkForComment();
          lastIndex = i + 1; // next class includes everything since the end of the prior class.
        }
      }
    }
    if (lastIndex < psiClass.getChildren().length) {
      if (lastIndex < 0) {
        lastIndex = 0;
      }
      // create a dummy trailer entry to cover anything after the last class.
      final MiscellaneousTextEntry miscellaneousTextEntry = new MiscellaneousTextEntry(
        psiClass.getChildren()[lastIndex],
        psiClass.getChildren()[psiClass.getChildren().length - 1],
        false, true
      );
      myContents.add(miscellaneousTextEntry);
      miscellaneousTextEntry.checkForComment();
    }
  }

  /**
   * Search for the end of the enumeration's fields.  It may be terminated with a semicolon, in which case we want
   * to point to the next item.  If no semicolon is found, we want to point to the enum's right brace.
   *
   * @param clazz
   * @return starting element index for the portion of the enumeration class following its enum declarations.
   */
  private static int findLastEnumTerminator(PsiClass clazz) {
    int startElementIndex = 0;
    // go until we hit RBrace (non-inclusive), or semicolon (inclusive)
    boolean foundLBrace = false;
    for (int j = 0; j < clazz.getChildren().length; j++) {
      PsiElement psiElement = clazz.getChildren()[j];
      if (!foundLBrace) {
        if (psiElement == clazz.getLBrace()) {
          foundLBrace = true;
        }
      }
      else {
        startElementIndex = j;
        if (psiElement == clazz.getRBrace()) {
          break;
        }
        if (psiElement instanceof PsiJavaToken &&
            ((PsiJavaToken)psiElement).getTokenType() == JavaTokenType.SEMICOLON)
        {
          break;
        }
      }
    }
    return startElementIndex;
  }

  private static int parseField(PsiElement child, MemberAttributes attributes, int i, final PsiClass psiClass) {
    attributes.field = (PsiField)child;
    LOG.debug("enter parseField: child=" +
              (child == null ? "null" : child.toString()));
    attributes.name = attributes.field.getName();
    final PsiClass containingClass = attributes.field.getContainingClass();
    final PsiTypeElement typeElement = attributes.field.getTypeElement();
    if (typeElement == null && containingClass != null) {
      attributes.type = containingClass.getName(); // if enum, use class as type
    }
    else if (typeElement != null) {
      attributes.type = typeElement.getText();
    }
    final PsiModifierList modifierList = attributes.field.getModifierList();
    if (modifierList != null) {
      attributes.modifierString = modifierList.getText();
    }
    attributes.modifiers = ModifierUtils.getModifierMask(attributes.modifierString);
    if (containingClass != null && containingClass.isInterface()) {
      // all fields in an interface are constants, hence "public static final"
      attributes.modifiers |= Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL;
    }
    final PsiExpression initializer = attributes.field.getInitializer();
    if (initializer instanceof PsiNewExpression) {
      final PsiElement lc = initializer.getLastChild();
      if (lc instanceof PsiAnonymousClass) {
        attributes.modifiers |= ModifierConstants.INIT_TO_ANON_CLASS;
      }
    }
    LOG.debug(
      "parseField: name=" +
      attributes.name +
      ", modifiers=" +
      Integer.toHexString(attributes.modifiers)
    );
    /**
     * handle multiple declarations here.  While this field declaration does not end
     * in a semicolon, skip whitespace and intervening comma; move child ahead to
     * next field.  This prevents declarations like "int b, a;" from being split.
     */
    PsiElement myChild = child;
    boolean done = false;
    while (!done) {
      PsiElement lastElement = myChild.getLastChild();
      LOG.debug("parseField: getLastChild=" +
                (lastElement == null ? "null" : lastElement.toString()));
      if (lastElement == null) break;
      if (lastElement instanceof PsiErrorElement) break;
      if (lastElement instanceof PsiJavaToken &&
          ((PsiJavaToken)lastElement).getTokenType() == JavaTokenType.SEMICOLON)
      {
        break;
      }
      if (lastElement instanceof PsiComment) {
        break;
      }
      // skip to next field
      LOG.debug(
        "parseField:skipping to next field, i=" +
        i +
        ", psiClass=" + psiClass.getName()
      );
      LOG.debug(
        "parseField: psiClass children array length=" +
        psiClass.getChildren().length
      );
      while (++i < psiClass.getChildren().length) {
        myChild = psiClass.getChildren()[i];
        LOG.debug("parseField: myChild=" + myChild.toString());
        if (myChild instanceof PsiField) {
          done = true;
          break;
        }
      }
    }
    return i;
  }

  private static void parseMethod(PsiElement child, MemberAttributes attributes, final PsiSearchHelper psh) {
    attributes.method = (PsiMethod)child;
    attributes.name = attributes.method.getName();
    final PsiTypeElement returnTypeElement = attributes.method.getReturnTypeElement();
    if (returnTypeElement == null) {
      attributes.type = "null";
    }
    else {
      attributes.type = returnTypeElement.getText();
    }
    attributes.modifierString = attributes.method.getModifierList().getText();
    attributes.modifiers = ModifierUtils.getModifierMask(attributes.modifierString);
    attributes.nParameters = attributes.method.getParameterList().getParameters().length;
    final PsiClass containingClass = attributes.method.getContainingClass();
    if (containingClass != null && containingClass.isInterface()) {
      // methods in an interface are always considered public
      attributes.modifiers |= Modifier.PUBLIC;
    }
    // set any additional "modifier" flags.
//        final Query<MethodSignatureBackedByPsiMethod> sm2 = SuperMethodsSearch.search(attributes.method,
//                attributes.method.getContainingClass(), false, false);
//        for (MethodSignatureBackedByPsiMethod msb : sm2) {
//            System.out.println(msb.getMethod().getName());
//        }                  // seems to be no different than method.findSuperMethods()

    attributes.modifiers |= isCanonicalOrInterface(attributes.method, attributes);
    final SearchScope ss = psh.getUseScope(child);
    final Query<PsiMethod> query = OverridingMethodsSearch.search(attributes.method, ss, true);
    final PsiMethod method = query.findFirst();
    if (method != null) {
      final Collection<PsiMethod> overridersCollection = query.findAll();
      final PsiMethod[] overriders = overridersCollection.toArray(new PsiMethod[overridersCollection.size()]);
      LOG.debug(
        "method " +
        attributes.method.toString() +
        " is overridden; has " +
        overriders.length + " overriding methods:"
      );
      dumpMethodNames(overriders);
      if (attributes.method.getBody() == null) {
        attributes.modifiers |= ModifierConstants.IMPLEMENTED;
      }
      else {
        attributes.modifiers |= ModifierConstants.OVERRIDDEN;
      }
    }
    // Determine if this method overrides another.
    final PsiMethod[] superMethods = attributes.method.findSuperMethods(false);
    LOG.debug("method " + attributes.method.toString() + " has " + superMethods.length + " supermethods");
    dumpMethodNames(superMethods);
    if (superMethods.length > 0) {
      // Determine if supermethod is abstract or interface; if so, assign IMPLEMENTING attribute.
      PsiMethod superMethod = superMethods[0];
      boolean abztract = superMethod.getModifierList().hasModifierProperty(PsiModifier.ABSTRACT);
      PsiClass superclass = superMethod.getContainingClass();
      if (abztract || (
        superclass != null && superclass.isInterface()))
      {
        attributes.modifiers |= ModifierConstants.IMPLEMENTING;
      }
      else {
        attributes.modifiers |= ModifierConstants.OVERRIDING;
      }
    }
    if (attributes.method.isConstructor()) {
      attributes.modifiers |= ModifierConstants.CONSTRUCTOR;
    }
    // Getter/setter cannot be determined here because definition of what a getter/setter
    // is can vary from rule to rule.  Do it at the time of rule matching.
    else if ((attributes.modifiers & ModifierConstants.CANONICAL) == 0) {
      attributes.modifiers |= ModifierConstants.OTHER_METHOD;
    }
    LOG.debug(
      "method " +
      attributes.name +
      " attributes: " +
      ModifierConstants.toString(attributes.modifiers)
    );
  }

  private static int isCanonicalOrInterface(PsiMethod method, MemberAttributes attributes) {
    PsiElement methodParent = method.getParent();
    LOG.debug("checking to see if " + method.getName() + " of " + methodParent + " is canonical");
    PsiMethod[] superMethods = method.findSuperMethods();
    LOG.debug("result of findSuperMethods for " + method.getName() + ": size=" + superMethods.length);
    // check to see if this method is canonical (inherited from Object).
    for (PsiMethod m : superMethods) {
      PsiElement parent = m.getParent();
      LOG.debug(
        "supermethod " +
        m.getName() +
        " belongs to " +
        parent
      );
      if (parent instanceof PsiClass) {
        final PsiClass psiClass = ((PsiClass)parent);
        if (psiClass.isInterface()) {
          LOG.debug("method " + method.toString() + " implements interface");
          attributes.interfaceName = psiClass.getName();
        }
        PsiElement superclass = psiClass.getSuperClass();
        if (superclass == null) {
          // m's class must be java.lang.Object; it's the only class with a null
          // superclass.
          LOG.debug("method " + method.toString() + " is canonical");
          return ModifierConstants.CANONICAL;
        }
        else {
          int result = isCanonicalOrInterface(m, attributes);
//                    int result = 0;
//                    if (!superclass.toString().equalsIgnoreCase("psiclass:object"))
//                    {
//                        result = isCanonicalOrInterface(m, attributes);
//                    }
          if (result > 0) {
            LOG.debug("Returning result from canonical check as " + result);
            return result;
          }
        }
      }
    }
    return 0;
  }

  private static void dumpMethodNames(PsiMethod[] methods) {
    for (int j = 0; j < methods.length; j++) {
      LOG.debug(
        j +
        ":" +
        methods[j].toString() +
        " of class " +
        methods[j].getParent().toString()
      );
    }
  }

  private static void parseClassAttributes(@NotNull PsiClass child, @NotNull MemberAttributes attributes) {
    attributes.childClass = child;
    attributes.name = attributes.childClass.getName();
    if (attributes.name == null) {
      attributes.name = "";
    }
    final PsiModifierList modifierList = child.getModifierList();
    if (modifierList != null) {
      attributes.modifierString = modifierList.getText();
    }
    attributes.modifiers = ModifierUtils.getModifierMask(attributes.modifierString);
    attributes.type = attributes.childClass.isEnum() ? "enum" : "class";
    if (attributes.childClass.isEnum()) {
      attributes.modifiers |= ModifierConstants.ENUM;
    }
  }

  private static void parseClassInitializer(PsiElement child, MemberAttributes attributes) {
    attributes.classInitializer = (PsiClassInitializer)child;
    attributes.name = "";
    final PsiModifierList modifierList = attributes.classInitializer.getModifierList();
    if (modifierList != null) {
      attributes.modifierString = modifierList.getText();
    }
    attributes.modifiers = ModifierUtils.getModifierMask(attributes.modifierString) |
                           ModifierConstants.INITIALIZER;
  }

  public void emit(Emitter emitter) {
    // First emit the text up to and including the left brace.
    super.emit(emitter);
    // now emit all children.
    emitter.emitRuleInstances(getResultRuleInstances());
  }

  public DefaultMutableTreeNode addToPopupTree(DefaultMutableTreeNode parent, RearrangerSettings settings) {
    DefaultMutableTreeNode result = super.addToPopupTree(parent, settings);
    // now add class contents, if any
    if (getResultRuleInstances() != null) {
      for (RuleInstance instance : getResultRuleInstances()) {
        instance.addRuleInstanceToPopupTree(result, settings);
      }
    }
    return result;
  }

  /** Rearranges the contents of this PsiClass according to supplied rules. */
  public void rearrangeContents() {
    buildMethodCallGraph();

    LOG.debug("identifying setters and extracted (related) methods");
    for (ClassContentsEntry contentsEntry : getContents()) {
      if (contentsEntry instanceof RelatableEntry) {
        ((RelatableEntry)contentsEntry).determineSettersAndMethodCalls(mySettings, getContents());
      }
    }
    LOG.debug("relating extracted methods");
    for (ClassContentsEntry contentsEntry : getContents()) {
      if (contentsEntry instanceof RelatableEntry) {
        ((RelatableEntry)contentsEntry).determineExtractedMethod(mySettings.getExtractedMethodsSettings());
      }
    }
    // Remove any cycles in the related method graph.
    MethodEntry.eliminateCycles(getContents());
    // Check for overloaded extracted methods; if configured to be kept together, attach subsequent
    // methods to the first and remove them from consideration for other alignment.
    MethodEntry.handleOverloadedMethods(getContents(), mySettings);
    final GenericRearranger classContentsRearranger =
      new GenericRearranger(mySettings.getItemOrderAttributeList(),
                            myContents,
                            myNestingLevel,
                            mySettings)
      {
        public void rearrangeRelatedItems(List<ClassContentsEntry> entries,
                                          List<RuleInstance> ruleInstanceList)
        {
          for (RuleInstance ruleInstance : ruleInstanceList) {
            ruleInstance.rearrangeRuleItems(entries, mySettings);
          }
        }
      };
    myResultRuleInstances = classContentsRearranger.rearrangeEntries();
  }

  private void buildMethodCallGraph() {
    if (mySettings.getExtractedMethodsSettings().isMoveExtractedMethods() ||
        mySettings.isKeepGettersSettersTogether())
    {
      LOG.debug("building method call & getter-setter graph");
      for (ClassContentsEntry contentsEntry : getContents()) {
        if (contentsEntry instanceof RelatableEntry) {
          ((RelatableEntry)contentsEntry).determineGetterSetterAndExtractedMethodStatus(mySettings);
        }
      }
    }
  }

// End Methods of Interface IFilePopupEntry

  public final List<ClassContentsEntry> getContents() {
    return myContents;
  }

  public List<RuleInstance> getResultRuleInstances() {
    return myResultRuleInstances;
  }
}

