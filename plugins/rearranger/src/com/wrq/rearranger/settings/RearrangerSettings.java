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
package com.wrq.rearranger.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.wrq.rearranger.Rearranger;
import com.wrq.rearranger.settings.attributeGroups.AttributeGroup;
import com.wrq.rearranger.settings.attributeGroups.ClassAttributes;
import com.wrq.rearranger.settings.attributeGroups.GetterSetterDefinition;
import com.wrq.rearranger.settings.attributeGroups.ItemAttributes;
import com.wrq.rearranger.util.RearrangerConstants;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * Holds two lists of rearrangement settings, one for the outer level classes, the other for class members.  Each
 * list contains objects of type CommonAttributes.
 */
public final class RearrangerSettings {

// ------------------------------ FIELDS ------------------------------

  // BEGINNING OF FIELDS
  private static final Logger LOG                                    = Logger.getInstance("#" + RearrangerSettings.class.getName());
  public static final  int    OVERLOADED_ORDER_RETAIN_ORIGINAL       = 0;
  public static final  int    OVERLOADED_ORDER_ASCENDING_PARAMETERS  = 1;
  public static final  int    OVERLOADED_ORDER_DESCENDING_PARAMETERS = 2;

  private final List<AttributeGroup>       myItemOrderAttributeList;
  private final List<AttributeGroup>       myClassOrderAttributeList;
  private       RelatedMethodsSettings     myRelatedMethodsSettings;
  private       boolean                    myKeepGettersSettersTogether;
  private       boolean                    myKeepGettersSettersWithProperty;
  private       boolean                    myKeepOverloadedMethodsTogether;
  private       String                     myGlobalCommentPattern;
  private       boolean                    myAskBeforeRearranging;
  private       boolean                    myRearrangeInnerClasses;
  private       boolean                    myShowParameterTypes;
  private       boolean                    myShowParameterNames;
  private       boolean                    myShowFields;
  private       boolean                    myShowTypeAfterMethod;
  private       boolean                    myShowRules;
  private       boolean                    myShowMatchedRules;
  private       boolean                    myShowComments;
  private       ForceBlankLineSetting      myAfterClassLBrace;
  private       ForceBlankLineSetting      myAfterClassRBrace;
  private       ForceBlankLineSetting      myBeforeClassRBrace;
  private       ForceBlankLineSetting      beforeMethodLBrace;
  private       ForceBlankLineSetting      myAfterMethodLBrace;
  private       ForceBlankLineSetting      myAfterMethodRBrace;
  private       ForceBlankLineSetting      myBeforeMethodRBrace;
  private       boolean                    myRemoveBlanksInsideCodeBlocks;
  private       ForceBlankLineSetting      myNewLinesAtEOF;
  private       int                        myOverloadedOrder;
  private       GetterSetterDefinition     myDefaultGSDefinition;
  private       List<PrimaryMethodSetting> myPrimaryMethodList;

// -------------------------- STATIC METHODS --------------------------

  static public int getIntAttribute(final Element me, final String attr) {
    return getIntAttribute(me, attr, 0);
  }

  static public boolean getBooleanAttribute(final Element me, final String attr) {
    return getBooleanAttribute(me, attr, false);
  }

  @Nullable
  public static RearrangerSettings getDefaultSettings() throws IllegalStateException {
    LOG.debug("enter loadDefaultSettings");
    URL settingsURL = Rearranger.class.getClassLoader().getResource(RearrangerConstants.DEFAULT_CONFIG_PATH);
    if (settingsURL == null) {
      throw new IllegalStateException(String.format(
        "Can't find default configuration (tried path '%s'",
        RearrangerConstants.DEFAULT_CONFIG_PATH
      ));
    }
    LOG.debug("settings URL=" + settingsURL.getFile());
    try {
      InputStream is = settingsURL.openStream();
      return getSettingsFromStream(is);
    }
    catch (IOException e) {
      LOG.debug("getDefaultSettings:" + e);
      return null;
    }
  }
  
  @Nullable
  public static RearrangerSettings getSettingsFromStream(InputStream is) {
    Document document = null;
    try {
      SAXBuilder builder = new SAXBuilder();
      document = builder.build(is);
    }
    catch (JDOMException jde) {
      LOG.debug("JDOM exception building document:" + jde);
      return null;
    }
    catch (IOException e) {
      LOG.debug("I/O exception building document:" + e);
      return null;
    }
    finally {
      
      if (is != null) {
        try {
          is.close();
        }
        catch (IOException e) {
          // Do nothing
        }
      }
    }
    Element app = document.getRootElement();
    Element component = null;
    if (app.getName().equals("application")) {
      for (Object o : app.getChildren()) {
        Element child = (Element)o;
        if (child.getName().equals("component") &&
            child.getAttribute("name") != null &&
            child.getAttribute("name").getValue().equals(Rearranger.COMPONENT_NAME)) {
          component = child;
          break;
        }
      }
    }
    else {
      if (app.getName().equals("component") &&
          app.getAttribute("name") != null &&
          app.getAttribute("name").getValue().equals(Rearranger.COMPONENT_NAME))
      {
        component = app;
      }
    }
    if (component != null) {
      Element rearranger = component.getChild(Rearranger.COMPONENT_NAME);
      if (rearranger != null) {
        RearrangerSettings rs = new RearrangerSettings();
        rs.readExternal(rearranger);
        return rs;
      }
    }
    return null;
  }

// Level 1 methods

  /** @param entry JDOM element named "Rearranger" which contains setting values as attributes. */
  public final void readExternal(final Element entry) {
    final Element items = entry.getChild("Items");
    final Element classes = entry.getChild("Classes");

    final List itemList = items.getChildren();
    final List classList = classes.getChildren();

    ListIterator li;
    li = itemList.listIterator();
    while (li.hasNext())
//        for (Element element : itemList)
    {
      Element element = (Element)li.next();
      myItemOrderAttributeList.add(ItemAttributes.readExternal(element));
    }
    li = classList.listIterator();
//        for (Element element : classList)
    while (li.hasNext()) {
      Element element = (Element)li.next();
      myClassOrderAttributeList.add(ClassAttributes.readExternal(element));
    }
    final Element gsd = entry.getChild("DefaultGetterSetterDefinition");
    myDefaultGSDefinition = GetterSetterDefinition.readExternal(gsd);
    final Element relatedItems = entry.getChild("RelatedMethods");
    myRelatedMethodsSettings = RelatedMethodsSettings.readExternal(relatedItems);
    myKeepGettersSettersTogether = getBooleanAttribute(entry, "KeepGettersSettersTogether", true);
    myKeepGettersSettersWithProperty = getBooleanAttribute(entry, "KeepGettersSettersWithProperty", false);
    myKeepOverloadedMethodsTogether = getBooleanAttribute(entry, "KeepOverloadedMethodsTogether", true);
    final Attribute attr = getAttribute(entry, "globalCommentPattern");
    myAskBeforeRearranging = getBooleanAttribute(entry, "ConfirmBeforeRearranging", false);
    myRearrangeInnerClasses = getBooleanAttribute(entry, "RearrangeInnerClasses", false);
    myGlobalCommentPattern = (attr == null ? "" : attr.getValue());
    myOverloadedOrder = getIntAttribute(entry, "overloadedOrder", OVERLOADED_ORDER_RETAIN_ORIGINAL);
    myShowParameterTypes = getBooleanAttribute(entry, "ShowParameterTypes", true);
    myShowParameterNames = getBooleanAttribute(entry, "ShowParameterNames", true);
    myShowFields = getBooleanAttribute(entry, "ShowFields", true);
    myShowRules = getBooleanAttribute(entry, "ShowRules", false);
    myShowMatchedRules = getBooleanAttribute(entry, "ShowMatchedRules", false);
    myShowComments = getBooleanAttribute(entry, "ShowComments", false);
    myShowTypeAfterMethod = getBooleanAttribute(entry, "ShowTypeAfterMethod", true);
    myRemoveBlanksInsideCodeBlocks = getBooleanAttribute(entry, "RemoveBlanksInsideCodeBlocks", false);
    myAfterClassLBrace = ForceBlankLineSetting.readExternal(entry, false, true, ForceBlankLineSetting.CLASS_OBJECT, "AfterClassLBrace");
    myAfterClassRBrace = ForceBlankLineSetting.readExternal(entry, false, false, ForceBlankLineSetting.CLASS_OBJECT, "AfterClassRBrace");
    myBeforeClassRBrace = ForceBlankLineSetting.readExternal(entry, true, false, ForceBlankLineSetting.CLASS_OBJECT, "BeforeClassRBrace");
    beforeMethodLBrace = ForceBlankLineSetting.readExternal(entry, true, true, ForceBlankLineSetting.METHOD_OBJECT, "BeforeMethodLBrace");
    myAfterMethodLBrace = ForceBlankLineSetting.readExternal(entry, false, true, ForceBlankLineSetting.METHOD_OBJECT, "AfterMethodLBrace");
    myAfterMethodRBrace = ForceBlankLineSetting.readExternal(entry, false, false, ForceBlankLineSetting.METHOD_OBJECT, "AfterMethodRBrace");
    myBeforeMethodRBrace = ForceBlankLineSetting.readExternal(entry, true, false, ForceBlankLineSetting.METHOD_OBJECT, "BeforeMethodRBrace");
    myNewLinesAtEOF = ForceBlankLineSetting.readExternal(entry, false, false, ForceBlankLineSetting.EOF_OBJECT, "NewlinesAtEOF");
  }

// Level 2 methods

  @Nullable
  static public Attribute getAttribute(Element element, String attributeName) {
    if (element == null) return null;
    return element.getAttribute(attributeName);
  }

  static public int getIntAttribute(final Element item, final String attributeName, int defaultValue) {
    if (item == null) return defaultValue;
    final Attribute a = item.getAttribute(attributeName);
    if (a == null) return defaultValue;
    final String r = a.getValue();
    if (r == null) return defaultValue;
    if (r.length() == 0) return defaultValue;
    return Integer.parseInt(r);
  }

  static public boolean getBooleanAttribute(final Element me, final String attr, boolean defaultValue) {
    if (me == null) return defaultValue;
    final Attribute a = me.getAttribute(attr);
    if (a == null) return defaultValue;
    final String r = a.getValue();
    if (r == null) return defaultValue;
    return (r.equalsIgnoreCase("true"));
  }

  @Nullable
  public static RearrangerSettings getSettingsFromFile(File f) {
    try {
      final FileInputStream stream = new FileInputStream(f);
      try {
        return getSettingsFromStream(stream);
      }
      finally {
        try {
          stream.close();
        }
        catch (IOException e) {
          // Ignore.
        }
      }
    }
    catch (FileNotFoundException e) {
      LOG.debug("getSettingsFromFile:" + e);
    }
    return null;
  }

// --------------------------- CONSTRUCTORS ---------------------------

  // END OF ALL FIELDS
  public RearrangerSettings() {
    myItemOrderAttributeList = new ArrayList<AttributeGroup>();
    myClassOrderAttributeList = new ArrayList<AttributeGroup>();
    myRelatedMethodsSettings = new RelatedMethodsSettings();
    myKeepGettersSettersTogether = false;
    myKeepGettersSettersWithProperty = false;
    myKeepOverloadedMethodsTogether = false;
    myGlobalCommentPattern = "";
    myOverloadedOrder = OVERLOADED_ORDER_RETAIN_ORIGINAL;
    myAskBeforeRearranging = false;
    myRearrangeInnerClasses = false;
    myShowParameterTypes = true;
    myShowParameterNames = false;
    myShowFields = true;
    myShowTypeAfterMethod = true;
    myShowRules = false;
    myShowMatchedRules = false;
    myShowComments = false;
    myRemoveBlanksInsideCodeBlocks = false;
    myAfterClassLBrace = new ForceBlankLineSetting(false, true, ForceBlankLineSetting.CLASS_OBJECT, "AfterClassLBrace");
    myAfterClassRBrace = new ForceBlankLineSetting(false, false, ForceBlankLineSetting.CLASS_OBJECT, "AfterClassRBrace");
    myBeforeClassRBrace = new ForceBlankLineSetting(true, false, ForceBlankLineSetting.CLASS_OBJECT, "BeforeClassRBrace");
    beforeMethodLBrace = new ForceBlankLineSetting(true, true, ForceBlankLineSetting.METHOD_OBJECT, "BeforeMethodLBrace");
    myAfterMethodLBrace = new ForceBlankLineSetting(false, true, ForceBlankLineSetting.METHOD_OBJECT, "AfterMethodLBrace");
    myBeforeMethodRBrace = new ForceBlankLineSetting(true, false, ForceBlankLineSetting.METHOD_OBJECT, "BeforeMethodRBrace");
    myAfterMethodRBrace = new ForceBlankLineSetting(false, false, ForceBlankLineSetting.METHOD_OBJECT, "AfterMethodRBrace");
    myNewLinesAtEOF = new ForceBlankLineSetting(false, false, ForceBlankLineSetting.EOF_OBJECT, "NewlinesAtEOF");
    myDefaultGSDefinition = new GetterSetterDefinition();
    myPrimaryMethodList = new LinkedList<PrimaryMethodSetting>();
  }

// --------------------- GETTER / SETTER METHODS ---------------------

  public ForceBlankLineSetting getAfterClassLBrace() {
    return myAfterClassLBrace;
  }

  public ForceBlankLineSetting getAfterClassRBrace() {
    return myAfterClassRBrace;
  }

  public ForceBlankLineSetting getBeforeMethodLBrace() {
    return beforeMethodLBrace;
  }

  public ForceBlankLineSetting getAfterMethodLBrace() {
    return myAfterMethodLBrace;
  }

  public ForceBlankLineSetting getAfterMethodRBrace() {
    return myAfterMethodRBrace;
  }

  public ForceBlankLineSetting getBeforeClassRBrace() {
    return myBeforeClassRBrace;
  }

  public ForceBlankLineSetting getBeforeMethodRBrace() {
    return myBeforeMethodRBrace;
  }

  public final List<AttributeGroup> getClassOrderAttributeList() {
    return myClassOrderAttributeList;
  }

  public GetterSetterDefinition getDefaultGSDefinition() {
    return myDefaultGSDefinition;
  }

// Level 1 methods

  public String getGlobalCommentPattern() {
    return myGlobalCommentPattern;
  }

// Level 2 methods

  public void setGlobalCommentPattern(String globalCommentPattern) {
    myGlobalCommentPattern = globalCommentPattern;
  }

// end of Level 2 methods
// end of Level 1 methods

  public final List<AttributeGroup> getItemOrderAttributeList() {
    return myItemOrderAttributeList;
  }

  public ForceBlankLineSetting getNewLinesAtEOF() {
    return myNewLinesAtEOF;
  }

// Level 1 methods

  public int getOverloadedOrder() {
    return myOverloadedOrder;
  }

// Level 2 methods

  public void setOverloadedOrder(int overloadedOrder) {
    myOverloadedOrder = overloadedOrder;
  }

// end of Level 2 methods
// end of Level 1 methods

  public RelatedMethodsSettings getExtractedMethodsSettings() {
    return myRelatedMethodsSettings;
  }

// Level 1 methods

  public boolean isAskBeforeRearranging() {
    return myAskBeforeRearranging;
  }

// Level 2 methods

  public void setAskBeforeRearranging(boolean askBeforeRearranging) {
    myAskBeforeRearranging = askBeforeRearranging;
  }

  public boolean isKeepGettersSettersTogether() {
    return myKeepGettersSettersTogether;
  }

// Level 2 methods

  public void setKeepGettersSettersTogether(boolean keepGettersSettersTogether) {
    myKeepGettersSettersTogether = keepGettersSettersTogether;
  }

  public boolean isKeepGettersSettersWithProperty() {
    return myKeepGettersSettersWithProperty;
  }

  public void setKeepGettersSettersWithProperty(boolean keepGettersSettersWithProperty) {
    myKeepGettersSettersWithProperty = keepGettersSettersWithProperty;
  }
// end of Level 2 methods
// end of Level 1 methods
// Level 1 methods

  public boolean isKeepOverloadedMethodsTogether() {
    return myKeepOverloadedMethodsTogether;
  }

// Level 2 methods

  public void setKeepOverloadedMethodsTogether(boolean keepOverloadedMethodsTogether) {
    myKeepOverloadedMethodsTogether = keepOverloadedMethodsTogether;
  }

  // end of Level 2 methods
// end of Level 1 methods
// Level 1 methods
  public boolean isRearrangeInnerClasses() {
    return myRearrangeInnerClasses;
  }

  public void setRearrangeInnerClasses(boolean rearrangeInnerClasses) {
    myRearrangeInnerClasses = rearrangeInnerClasses;
  }

// end of Level 2 methods
// end of Level 1 methods
// Level 1 methods

  public boolean isRemoveBlanksInsideCodeBlocks() {
    return myRemoveBlanksInsideCodeBlocks;
  }

// Level 2 methods

  public void setRemoveBlanksInsideCodeBlocks(boolean removeBlanksInsideCodeBlocks) {
    myRemoveBlanksInsideCodeBlocks = removeBlanksInsideCodeBlocks;
  }

  public boolean isShowComments() {
    return myShowComments;
  }

  public void setShowComments(boolean showComments) {
    myShowComments = showComments;
  }

// end of Level 2 methods
// end of Level 1 methods
// Level 1 methods

  public boolean isShowFields() {
    return myShowFields;
  }

// Level 2 methods

  public void setShowFields(boolean showFields) {
    myShowFields = showFields;
  }

  public boolean isShowMatchedRules() {
    return myShowMatchedRules;
  }

  public void setShowMatchedRules(boolean showMatchedRules) {
    myShowMatchedRules = showMatchedRules;
  }

  public boolean isShowParameterNames() {
    return myShowParameterNames;
  }

// Level 2 methods

  public void setShowParameterNames(boolean showParameterNames) {
    myShowParameterNames = showParameterNames;
  }

// end of Level 2 methods
// end of Level 1 methods
// Level 1 methods

  public boolean isShowParameterTypes() {
    return myShowParameterTypes;
  }

// Level 2 methods

  public void setShowParameterTypes(boolean showParameterTypes) {
    myShowParameterTypes = showParameterTypes;
  }

  // end of Level 2 methods
// end of Level 1 methods
// Level 1 methods
  public boolean isShowRules() {
    return myShowRules;
  }

  public void setShowRules(boolean showRules) {
    myShowRules = showRules;
  }

  // end of Level 2 methods
// end of Level 1 methods
  public boolean isShowTypeAfterMethod() {
    return myShowTypeAfterMethod;
  }

  public void setShowTypeAfterMethod(boolean showTypeAfterMethod) {
    myShowTypeAfterMethod = showTypeAfterMethod;
  }

// ------------------------ CANONICAL METHODS ------------------------

  public boolean equals(final Object object) {
    if (!(object instanceof RearrangerSettings)) return false;
    final RearrangerSettings rs = (RearrangerSettings)object;
    if (rs.getClassOrderAttributeList().size() != getClassOrderAttributeList().size()) return false;
    if (rs.getItemOrderAttributeList().size() != getItemOrderAttributeList().size()) return false;
    for (int i = 0; i < getClassOrderAttributeList().size(); i++) {
      if (!getClassOrderAttributeList().get(i).equals(rs.getClassOrderAttributeList().get(i))) return false;
    }
    for (int i = 0; i < getItemOrderAttributeList().size(); i++) {
      if (!getItemOrderAttributeList().get(i).equals(rs.getItemOrderAttributeList().get(i))) return false;
    }
    if (!rs.getExtractedMethodsSettings().equals(getExtractedMethodsSettings())) return false;
    if (rs.isKeepGettersSettersTogether() != isKeepGettersSettersTogether()) return false;
    if (rs.isKeepGettersSettersWithProperty() != isKeepGettersSettersWithProperty()) return false;
    if (rs.isKeepOverloadedMethodsTogether() != isKeepOverloadedMethodsTogether()) return false;
    if (rs.myOverloadedOrder != myOverloadedOrder) return false;
    if (!rs.myGlobalCommentPattern.equals(myGlobalCommentPattern)) return false;
    if (rs.myAskBeforeRearranging != myAskBeforeRearranging) return false;
    if (rs.myRearrangeInnerClasses != myRearrangeInnerClasses) return false;
    if (rs.myShowParameterTypes != myShowParameterTypes) return false;
    if (rs.myShowParameterNames != myShowParameterNames) return false;
    if (rs.myShowFields != myShowFields) return false;
    if (rs.myShowTypeAfterMethod != myShowTypeAfterMethod) return false;
    if (rs.myShowRules != myShowRules) return false;
    if (rs.myShowMatchedRules != myShowMatchedRules) return false;
    if (rs.myShowComments != myShowComments) return false;
    if (rs.myRemoveBlanksInsideCodeBlocks != myRemoveBlanksInsideCodeBlocks) return false;
    if (!rs.myAfterClassLBrace.equals(myAfterClassLBrace)) return false;
    if (!rs.myAfterClassRBrace.equals(myAfterClassRBrace)) return false;
    if (!rs.myBeforeClassRBrace.equals(myBeforeClassRBrace)) return false;
    if (!rs.beforeMethodLBrace.equals(beforeMethodLBrace)) return false;
    if (!rs.myAfterMethodLBrace.equals(myAfterMethodLBrace)) return false;
    if (!rs.myAfterMethodRBrace.equals(myAfterMethodRBrace)) return false;
    if (!rs.myBeforeMethodRBrace.equals(myBeforeMethodRBrace)) return false;
    if (!rs.myNewLinesAtEOF.equals(myNewLinesAtEOF)) return false;
    if (!rs.myDefaultGSDefinition.equals(myDefaultGSDefinition)) return false;
    if (myPrimaryMethodList.size() != rs.myPrimaryMethodList.size()) return false;
    for (int i = 0; i < myPrimaryMethodList.size(); i++) {
      PrimaryMethodSetting pms = myPrimaryMethodList.get(i);
      PrimaryMethodSetting rspms = rs.myPrimaryMethodList.get(i);
      if (!rspms.equals(pms)) return false;
    }
    return true;
  }

// -------------------------- OTHER METHODS --------------------------

  public void addClass(@NotNull AttributeGroup group) {
    myClassOrderAttributeList.add(group);
  }
  
  public final void insertClass(@NotNull final AttributeGroup ca, final int index) {
    if (myClassOrderAttributeList.size() < index) {
      myClassOrderAttributeList.add(ca);
    }
    else {
      myClassOrderAttributeList.add(index, ca);
    }
  }

  public void addItem(@NotNull AttributeGroup group) {
    myItemOrderAttributeList.add(group);
  }
  
  public final void insertItem(@NotNull final AttributeGroup ia, final int index) {
    if (myItemOrderAttributeList.size() < index) {
      myItemOrderAttributeList.add(ia);
    }
    else {
      myItemOrderAttributeList.add(index, ia);
    }
  }

  public final RearrangerSettings deepCopy() {
    final RearrangerSettings result = new RearrangerSettings();
    for (AttributeGroup itemAttributes : myItemOrderAttributeList) {
      result.myItemOrderAttributeList.add(itemAttributes.deepCopy());
    }
    for (AttributeGroup classAttributes : myClassOrderAttributeList) {
      result.myClassOrderAttributeList.add(classAttributes.deepCopy());
    }
    result.myRelatedMethodsSettings = myRelatedMethodsSettings.deepCopy();
    result.myKeepGettersSettersTogether = myKeepGettersSettersTogether;
    result.myKeepGettersSettersWithProperty = myKeepGettersSettersWithProperty;
    result.myKeepOverloadedMethodsTogether = myKeepOverloadedMethodsTogether;
    result.myGlobalCommentPattern = myGlobalCommentPattern;
    result.myOverloadedOrder = myOverloadedOrder;
    result.myAskBeforeRearranging = myAskBeforeRearranging;
    result.myRearrangeInnerClasses = myRearrangeInnerClasses;
    result.myShowParameterNames = myShowParameterNames;
    result.myShowParameterTypes = myShowParameterTypes;
    result.myShowFields = myShowFields;
    result.myShowTypeAfterMethod = myShowTypeAfterMethod;
    result.myShowRules = myShowRules;
    result.myShowMatchedRules = myShowMatchedRules;
    result.myShowComments = myShowComments;
    result.myRemoveBlanksInsideCodeBlocks = myRemoveBlanksInsideCodeBlocks;
    result.myAfterClassLBrace = myAfterClassLBrace.deepCopy();
    result.myAfterClassRBrace = myAfterClassRBrace.deepCopy();
    result.myBeforeClassRBrace = myBeforeClassRBrace.deepCopy();
    result.beforeMethodLBrace = beforeMethodLBrace.deepCopy();
    result.myAfterMethodLBrace = myAfterMethodLBrace.deepCopy();
    result.myAfterMethodRBrace = myAfterMethodRBrace.deepCopy();
    result.myBeforeMethodRBrace = myBeforeMethodRBrace.deepCopy();
    result.myNewLinesAtEOF = myNewLinesAtEOF.deepCopy();
    result.myDefaultGSDefinition = myDefaultGSDefinition.deepCopy();
    return result;
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public void writeSettingsToFile(File f) {
    Element component = new Element("component");
    component.setAttribute("name", Rearranger.COMPONENT_NAME);
    Element r = new Element(Rearranger.COMPONENT_NAME);
    component.getChildren().add(r);
    writeExternal(r);
    Format format = Format.getPrettyFormat();
    XMLOutputter outputter = new XMLOutputter(format);
    FileOutputStream fileOutputStream = null;
    try {
      fileOutputStream = new FileOutputStream(f);
      outputter.output(component, fileOutputStream);
      fileOutputStream.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally {
      if (fileOutputStream != null) {
        try {
          fileOutputStream.close();
        }
        catch (IOException e) {
          // Ignore
        }
      }
    }
  }

// end of Level 2 methods
// end of Level 1 methods

  public final void writeExternal(final Element entry) {
    final Element items = new Element("Items");
    final Element classes = new Element("Classes");
    entry.getChildren().add(items);
    entry.getChildren().add(classes);
    for (AttributeGroup item : myItemOrderAttributeList) {
      item.writeExternal(items);
    }
    for (AttributeGroup attributes : myClassOrderAttributeList) {
      attributes.writeExternal(classes);
    }
    final Element gsd = new Element("DefaultGetterSetterDefinition");
    entry.getChildren().add(gsd);
    myDefaultGSDefinition.writeExternal(gsd);
    final Element relatedItems = new Element("RelatedMethods");
    entry.getChildren().add(relatedItems);
    entry.setAttribute("KeepGettersSettersTogether", Boolean.valueOf(myKeepGettersSettersTogether).toString());
    entry.setAttribute("KeepGettersSettersWithProperty", Boolean.valueOf(myKeepGettersSettersWithProperty).toString());
    entry.setAttribute("KeepOverloadedMethodsTogether", Boolean.valueOf(myKeepOverloadedMethodsTogether).toString());
    entry.setAttribute("ConfirmBeforeRearranging", Boolean.valueOf(myAskBeforeRearranging).toString());
    entry.setAttribute("RearrangeInnerClasses", Boolean.valueOf(myRearrangeInnerClasses).toString());
    entry.setAttribute("globalCommentPattern", myGlobalCommentPattern);
    entry.setAttribute("overloadedOrder", "" + myOverloadedOrder);
    entry.setAttribute("ShowParameterTypes", Boolean.valueOf(myShowParameterTypes).toString());
    entry.setAttribute("ShowParameterNames", Boolean.valueOf(myShowParameterNames).toString());
    entry.setAttribute("ShowFields", Boolean.valueOf(myShowFields).toString());
    entry.setAttribute("ShowTypeAfterMethod", Boolean.valueOf(myShowTypeAfterMethod).toString());
    entry.setAttribute("ShowRules", Boolean.valueOf(myShowRules).toString());
    entry.setAttribute("ShowMatchedRules", Boolean.valueOf(myShowMatchedRules).toString());
    entry.setAttribute("ShowComments", Boolean.valueOf(myShowComments).toString());
    entry.setAttribute("RemoveBlanksInsideCodeBlocks", Boolean.valueOf(myRemoveBlanksInsideCodeBlocks).toString());
    myRelatedMethodsSettings.writeExternal(relatedItems);
    myAfterClassLBrace.writeExternal(entry);
    myAfterClassRBrace.writeExternal(entry);
    myBeforeClassRBrace.writeExternal(entry);
    beforeMethodLBrace.writeExternal(entry);
    myAfterMethodLBrace.writeExternal(entry);
    myAfterMethodRBrace.writeExternal(entry);
    myBeforeMethodRBrace.writeExternal(entry);
    myNewLinesAtEOF.writeExternal(entry);
  }
}
