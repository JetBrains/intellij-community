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
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

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
  private static final Logger logger                                 = Logger.getInstance("#" + RearrangerSettings.class.getName());
  public static final  int    OVERLOADED_ORDER_RETAIN_ORIGINAL       = 0;
  public static final  int    OVERLOADED_ORDER_ASCENDING_PARAMETERS  = 1;
  public static final  int    OVERLOADED_ORDER_DESCENDING_PARAMETERS = 2;
  private final List<AttributeGroup>   itemOrderAttributeList;
  private final List<AttributeGroup>   classOrderAttributeList;
  private       RelatedMethodsSettings relatedMethodsSettings;
  private       boolean                keepGettersSettersTogether;
  private       boolean                keepGettersSettersWithProperty;
  private       boolean                keepOverloadedMethodsTogether;
  private       String                 globalCommentPattern;
  private       boolean                askBeforeRearranging;
  private       boolean                rearrangeInnerClasses;
  private       boolean                showParameterTypes;
  private       boolean                showParameterNames;
  private       boolean                showFields;
  private       boolean                showTypeAfterMethod;
  private       boolean                showRules;
  private       boolean                showMatchedRules;
  private       boolean                showComments;
  private       ForceBlankLineSetting  afterClassLBrace;
  private       ForceBlankLineSetting  afterClassRBrace;
  private       ForceBlankLineSetting  beforeClassRBrace;
  private       ForceBlankLineSetting  beforeMethodLBrace;
  private       ForceBlankLineSetting  afterMethodLBrace;
  private       ForceBlankLineSetting  afterMethodRBrace;
  private       ForceBlankLineSetting  beforeMethodRBrace;
  private       boolean                removeBlanksInsideCodeBlocks;
  private       ForceBlankLineSetting  newlinesAtEOF;

  private int                        overloadedOrder;
  private GetterSetterDefinition     defaultGSDefinition;
  private List<PrimaryMethodSetting> primaryMethodList;

// -------------------------- STATIC METHODS --------------------------

  static public int getIntAttribute(final Element me, final String attr) {
    return getIntAttribute(me, attr, 0);
  }

  static public boolean getBooleanAttribute(final Element me, final String attr) {
    return getBooleanAttribute(me, attr, false);
  }

  public static RearrangerSettings getDefaultSettings() {
    logger.debug("enter loadDefaultSettings");
    URL settingsURL = Rearranger.class.getClassLoader().getResource("com/wrq/rearranger/defaultConfiguration.xml");
    logger.debug("settings URL=" + settingsURL.getFile());
    try {
      InputStream is = settingsURL.openStream();
      return getSettingsFromStream(is);
    }
    catch (IOException e) {
      logger.debug("getDefaultSettings:" + e);
      return null;
    }
  }

  public static RearrangerSettings getSettingsFromStream(InputStream is) {
    Document document = null;
    try {
      SAXBuilder builder = new SAXBuilder();
      document = builder.build(is);
    }
    catch (JDOMException jde) {
      logger.debug("JDOM exception building document:" + jde);
      return null;
    }
    catch (IOException e) {
      logger.debug("I/O exception building document:" + e);
      return null;
    }
    finally {
      if (is != null) {
        try {
          is.close();
        }
        catch (IOException e) {
        }
      }
    }
    Element app = document.getRootElement();
    Element component = null;
    if (app.getName().equals("application")) {
      ListIterator li = app.getChildren().listIterator();
      while (li.hasNext())
//            for (Element child : (List)((java.util.List)app.getChildren()))
      {
        Element child = (Element)li.next();
        if (child.getName().equals("component") &&
            child.getAttribute("name") != null &&
            child.getAttribute("name").getValue().equals(Rearranger.COMPONENT_NAME))
        {
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
      itemOrderAttributeList.add(((com.wrq.rearranger.settings.attributeGroups.AttributeGroup)ItemAttributes.readExternal(element)));
    }
    li = classList.listIterator();
//        for (Element element : classList)
    while (li.hasNext()) {
      Element element = (Element)li.next();
      classOrderAttributeList.add(((com.wrq.rearranger.settings.attributeGroups.AttributeGroup)ClassAttributes.readExternal(element)));
    }
    final Element gsd = entry.getChild("DefaultGetterSetterDefinition");
    defaultGSDefinition = GetterSetterDefinition.readExternal(gsd);
    final Element relatedItems = entry.getChild("RelatedMethods");
    relatedMethodsSettings = RelatedMethodsSettings.readExternal(relatedItems);
    keepGettersSettersTogether = getBooleanAttribute(entry, "KeepGettersSettersTogether", true);
    keepGettersSettersWithProperty = getBooleanAttribute(entry, "KeepGettersSettersWithProperty", false);
    keepOverloadedMethodsTogether = getBooleanAttribute(entry, "KeepOverloadedMethodsTogether", true);
    final Attribute attr = RearrangerSettings.getAttribute(entry, "globalCommentPattern");
    askBeforeRearranging = getBooleanAttribute(entry, "ConfirmBeforeRearranging", false);
    rearrangeInnerClasses = getBooleanAttribute(entry, "RearrangeInnerClasses", false);
    globalCommentPattern = (attr == null ? "" : ((java.lang.String)attr.getValue()));
    overloadedOrder = getIntAttribute(entry, "overloadedOrder", OVERLOADED_ORDER_RETAIN_ORIGINAL);
    showParameterTypes = getBooleanAttribute(entry, "ShowParameterTypes", true);
    showParameterNames = getBooleanAttribute(entry, "ShowParameterNames", true);
    showFields = getBooleanAttribute(entry, "ShowFields", true);
    showRules = getBooleanAttribute(entry, "ShowRules", false);
    showMatchedRules = getBooleanAttribute(entry, "ShowMatchedRules", false);
    showComments = getBooleanAttribute(entry, "ShowComments", false);
    showTypeAfterMethod = getBooleanAttribute(entry, "ShowTypeAfterMethod", true);
    removeBlanksInsideCodeBlocks = getBooleanAttribute(entry, "RemoveBlanksInsideCodeBlocks", false);
    afterClassLBrace = ForceBlankLineSetting.readExternal(entry, false, true, ForceBlankLineSetting.CLASS_OBJECT, "AfterClassLBrace");
    afterClassRBrace = ForceBlankLineSetting.readExternal(entry, false, false, ForceBlankLineSetting.CLASS_OBJECT, "AfterClassRBrace");
    beforeClassRBrace = ForceBlankLineSetting.readExternal(entry, true, false, ForceBlankLineSetting.CLASS_OBJECT, "BeforeClassRBrace");
    beforeMethodLBrace = ForceBlankLineSetting.readExternal(entry, true, true, ForceBlankLineSetting.METHOD_OBJECT, "BeforeMethodLBrace");
    afterMethodLBrace = ForceBlankLineSetting.readExternal(entry, false, true, ForceBlankLineSetting.METHOD_OBJECT, "AfterMethodLBrace");
    afterMethodRBrace = ForceBlankLineSetting.readExternal(entry, false, false, ForceBlankLineSetting.METHOD_OBJECT, "AfterMethodRBrace");
    beforeMethodRBrace = ForceBlankLineSetting.readExternal(entry, true, false, ForceBlankLineSetting.METHOD_OBJECT, "BeforeMethodRBrace");
    newlinesAtEOF = ForceBlankLineSetting.readExternal(entry, false, false, ForceBlankLineSetting.EOF_OBJECT, "NewlinesAtEOF");
  }

// Level 2 methods

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

  public static RearrangerSettings getSettingsFromFile(File f) {
    try {
      return getSettingsFromStream(new FileInputStream(f));
    }
    catch (FileNotFoundException e) {
      logger.debug("getSettingsFromFile:" + e);
    }
    return null;
  }

// --------------------------- CONSTRUCTORS ---------------------------

  // END OF ALL FIELDS
  public RearrangerSettings() {
    itemOrderAttributeList = new ArrayList<AttributeGroup>();
    classOrderAttributeList = new ArrayList<AttributeGroup>();
    relatedMethodsSettings = new RelatedMethodsSettings();
    keepGettersSettersTogether = false;
    keepGettersSettersWithProperty = false;
    keepOverloadedMethodsTogether = false;
    globalCommentPattern = "";
    overloadedOrder = OVERLOADED_ORDER_RETAIN_ORIGINAL;
    askBeforeRearranging = false;
    rearrangeInnerClasses = false;
    showParameterTypes = true;
    showParameterNames = false;
    showFields = true;
    showTypeAfterMethod = true;
    showRules = false;
    showMatchedRules = false;
    showComments = false;
    removeBlanksInsideCodeBlocks = false;
    afterClassLBrace = new ForceBlankLineSetting(false, true, ForceBlankLineSetting.CLASS_OBJECT, "AfterClassLBrace");
    afterClassRBrace = new ForceBlankLineSetting(false, false, ForceBlankLineSetting.CLASS_OBJECT, "AfterClassRBrace");
    beforeClassRBrace = new ForceBlankLineSetting(true, false, ForceBlankLineSetting.CLASS_OBJECT, "BeforeClassRBrace");
    beforeMethodLBrace = new ForceBlankLineSetting(true, true, ForceBlankLineSetting.METHOD_OBJECT, "BeforeMethodLBrace");
    afterMethodLBrace = new ForceBlankLineSetting(false, true, ForceBlankLineSetting.METHOD_OBJECT, "AfterMethodLBrace");
    beforeMethodRBrace = new ForceBlankLineSetting(true, false, ForceBlankLineSetting.METHOD_OBJECT, "BeforeMethodRBrace");
    afterMethodRBrace = new ForceBlankLineSetting(false, false, ForceBlankLineSetting.METHOD_OBJECT, "AfterMethodRBrace");
    newlinesAtEOF = new ForceBlankLineSetting(false, false, ForceBlankLineSetting.EOF_OBJECT, "NewlinesAtEOF");
    defaultGSDefinition = new GetterSetterDefinition();
    primaryMethodList = new LinkedList<PrimaryMethodSetting>();
  }

// --------------------- GETTER / SETTER METHODS ---------------------

  public ForceBlankLineSetting getAfterClassLBrace() {
    return afterClassLBrace;
  }

  public ForceBlankLineSetting getAfterClassRBrace() {
    return afterClassRBrace;
  }

  public ForceBlankLineSetting getBeforeMethodLBrace() {
    return beforeMethodLBrace;
  }

  public ForceBlankLineSetting getAfterMethodLBrace() {
    return afterMethodLBrace;
  }

  public ForceBlankLineSetting getAfterMethodRBrace() {
    return afterMethodRBrace;
  }

  public ForceBlankLineSetting getBeforeClassRBrace() {
    return beforeClassRBrace;
  }

  public ForceBlankLineSetting getBeforeMethodRBrace() {
    return beforeMethodRBrace;
  }

  public final List<AttributeGroup> getClassOrderAttributeList() {
    return classOrderAttributeList;
  }

  public GetterSetterDefinition getDefaultGSDefinition() {
    return defaultGSDefinition;
  }

// Level 1 methods

  public String getGlobalCommentPattern() {
    return globalCommentPattern;
  }

// Level 2 methods

  public void setGlobalCommentPattern(String globalCommentPattern) {
    this.globalCommentPattern = globalCommentPattern;
  }

// end of Level 2 methods
// end of Level 1 methods

  public final List<AttributeGroup> getItemOrderAttributeList() {
    return itemOrderAttributeList;
  }

  public ForceBlankLineSetting getNewlinesAtEOF() {
    return newlinesAtEOF;
  }

// Level 1 methods

  public int getOverloadedOrder() {
    return overloadedOrder;
  }

// Level 2 methods

  public void setOverloadedOrder(int overloadedOrder) {
    this.overloadedOrder = overloadedOrder;
  }

// end of Level 2 methods
// end of Level 1 methods

  public RelatedMethodsSettings getExtractedMethodsSettings() {
    return relatedMethodsSettings;
  }

// Level 1 methods

  public boolean isAskBeforeRearranging() {
    return askBeforeRearranging;
  }

// Level 2 methods

  public void setAskBeforeRearranging(boolean askBeforeRearranging) {
    this.askBeforeRearranging = askBeforeRearranging;
  }

  public boolean isKeepGettersSettersTogether() {
    return keepGettersSettersTogether;
  }

// Level 2 methods

  public void setKeepGettersSettersTogether(boolean keepGettersSettersTogether) {
    this.keepGettersSettersTogether = keepGettersSettersTogether;
  }

  public boolean isKeepGettersSettersWithProperty() {
    return keepGettersSettersWithProperty;
  }

  public void setKeepGettersSettersWithProperty(boolean keepGettersSettersWithProperty) {
    this.keepGettersSettersWithProperty = keepGettersSettersWithProperty;
  }
// end of Level 2 methods
// end of Level 1 methods
// Level 1 methods

  public boolean isKeepOverloadedMethodsTogether() {
    return keepOverloadedMethodsTogether;
  }

// Level 2 methods

  public void setKeepOverloadedMethodsTogether(boolean keepOverloadedMethodsTogether) {
    this.keepOverloadedMethodsTogether = keepOverloadedMethodsTogether;
  }

  // end of Level 2 methods
// end of Level 1 methods
// Level 1 methods
  public boolean isRearrangeInnerClasses() {
    return rearrangeInnerClasses;
  }

  public void setRearrangeInnerClasses(boolean rearrangeInnerClasses) {
    this.rearrangeInnerClasses = rearrangeInnerClasses;
  }

// end of Level 2 methods
// end of Level 1 methods
// Level 1 methods

  public boolean isRemoveBlanksInsideCodeBlocks() {
    return removeBlanksInsideCodeBlocks;
  }

// Level 2 methods

  public void setRemoveBlanksInsideCodeBlocks(boolean removeBlanksInsideCodeBlocks) {
    this.removeBlanksInsideCodeBlocks = removeBlanksInsideCodeBlocks;
  }

  public boolean isShowComments() {
    return showComments;
  }

  public void setShowComments(boolean showComments) {
    this.showComments = showComments;
  }

// end of Level 2 methods
// end of Level 1 methods
// Level 1 methods

  public boolean isShowFields() {
    return showFields;
  }

// Level 2 methods

  public void setShowFields(boolean showFields) {
    this.showFields = showFields;
  }

  public boolean isShowMatchedRules() {
    return showMatchedRules;
  }

  public void setShowMatchedRules(boolean showMatchedRules) {
    this.showMatchedRules = showMatchedRules;
  }

  public boolean isShowParameterNames() {
    return showParameterNames;
  }

// Level 2 methods

  public void setShowParameterNames(boolean showParameterNames) {
    this.showParameterNames = showParameterNames;
  }

// end of Level 2 methods
// end of Level 1 methods
// Level 1 methods

  public boolean isShowParameterTypes() {
    return showParameterTypes;
  }

// Level 2 methods

  public void setShowParameterTypes(boolean showParameterTypes) {
    this.showParameterTypes = showParameterTypes;
  }

  // end of Level 2 methods
// end of Level 1 methods
// Level 1 methods
  public boolean isShowRules() {
    return showRules;
  }

  public void setShowRules(boolean showRules) {
    this.showRules = showRules;
  }

  // end of Level 2 methods
// end of Level 1 methods
  public boolean isShowTypeAfterMethod() {
    return showTypeAfterMethod;
  }

  public void setShowTypeAfterMethod(boolean showTypeAfterMethod) {
    this.showTypeAfterMethod = showTypeAfterMethod;
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
    if (rs.overloadedOrder != overloadedOrder) return false;
    if (!rs.globalCommentPattern.equals(globalCommentPattern)) return false;
    if (rs.askBeforeRearranging != askBeforeRearranging) return false;
    if (rs.rearrangeInnerClasses != rearrangeInnerClasses) return false;
    if (rs.showParameterTypes != showParameterTypes) return false;
    if (rs.showParameterNames != showParameterNames) return false;
    if (rs.showFields != showFields) return false;
    if (rs.showTypeAfterMethod != showTypeAfterMethod) return false;
    if (rs.showRules != showRules) return false;
    if (rs.showMatchedRules != showMatchedRules) return false;
    if (rs.showComments != showComments) return false;
    if (rs.removeBlanksInsideCodeBlocks != removeBlanksInsideCodeBlocks) return false;
    if (!rs.afterClassLBrace.equals(afterClassLBrace)) return false;
    if (!rs.afterClassRBrace.equals(afterClassRBrace)) return false;
    if (!rs.beforeClassRBrace.equals(beforeClassRBrace)) return false;
    if (!rs.beforeMethodLBrace.equals(beforeMethodLBrace)) return false;
    if (!rs.afterMethodLBrace.equals(afterMethodLBrace)) return false;
    if (!rs.afterMethodRBrace.equals(afterMethodRBrace)) return false;
    if (!rs.beforeMethodRBrace.equals(beforeMethodRBrace)) return false;
    if (!rs.newlinesAtEOF.equals(newlinesAtEOF)) return false;
    if (!rs.defaultGSDefinition.equals(defaultGSDefinition)) return false;
    if (primaryMethodList.size() != rs.primaryMethodList.size()) return false;
    for (int i = 0; i < primaryMethodList.size(); i++) {
      PrimaryMethodSetting pms = primaryMethodList.get(i);
      PrimaryMethodSetting rspms = rs.primaryMethodList.get(i);
      if (!rspms.equals(pms)) return false;
    }
    return true;
  }

// -------------------------- OTHER METHODS --------------------------

  public final void addClass(final AttributeGroup ca, final int index) {
    if (classOrderAttributeList.size() < index) {
      classOrderAttributeList.add(ca);
    }
    else {
      classOrderAttributeList.add(index, ca);
    }
  }

  public final void addItem(final AttributeGroup ia, final int index) {
    if (itemOrderAttributeList.size() < index) {
      itemOrderAttributeList.add(ia);
    }
    else {
      itemOrderAttributeList.add(index, ia);
    }
  }

  public final RearrangerSettings deepCopy() {
    final RearrangerSettings result = new RearrangerSettings();
    for (AttributeGroup itemAttributes : itemOrderAttributeList) {
      result.itemOrderAttributeList.add(itemAttributes.deepCopy());
    }
    for (AttributeGroup classAttributes : classOrderAttributeList) {
      result.classOrderAttributeList.add(classAttributes.deepCopy());
    }
    result.relatedMethodsSettings = relatedMethodsSettings.deepCopy();
    result.keepGettersSettersTogether = keepGettersSettersTogether;
    result.keepGettersSettersWithProperty = keepGettersSettersWithProperty;
    result.keepOverloadedMethodsTogether = keepOverloadedMethodsTogether;
    result.globalCommentPattern = globalCommentPattern;
    result.overloadedOrder = overloadedOrder;
    result.askBeforeRearranging = askBeforeRearranging;
    result.rearrangeInnerClasses = rearrangeInnerClasses;
    result.showParameterNames = showParameterNames;
    result.showParameterTypes = showParameterTypes;
    result.showFields = showFields;
    result.showTypeAfterMethod = showTypeAfterMethod;
    result.showRules = showRules;
    result.showMatchedRules = showMatchedRules;
    result.showComments = showComments;
    result.removeBlanksInsideCodeBlocks = removeBlanksInsideCodeBlocks;
    result.afterClassLBrace = afterClassLBrace.deepCopy();
    result.afterClassRBrace = afterClassRBrace.deepCopy();
    result.beforeClassRBrace = beforeClassRBrace.deepCopy();
    result.beforeMethodLBrace = beforeMethodLBrace.deepCopy();
    result.afterMethodLBrace = afterMethodLBrace.deepCopy();
    result.afterMethodRBrace = afterMethodRBrace.deepCopy();
    result.beforeMethodRBrace = beforeMethodRBrace.deepCopy();
    result.newlinesAtEOF = newlinesAtEOF.deepCopy();
    result.defaultGSDefinition = defaultGSDefinition.deepCopy();
    return result;
  }

  public void writeSettingsToFile(File f) {
    Element component = new Element("component");
    component.setAttribute("name", Rearranger.COMPONENT_NAME);
    Element r = new Element(Rearranger.COMPONENT_NAME);
    component.getChildren().add(r);
    writeExternal(r);
    Format format = Format.getPrettyFormat();
    XMLOutputter outputter = new XMLOutputter(format);
    try {
      final FileOutputStream fileOutputStream = new FileOutputStream(f);
      outputter.output(component, fileOutputStream);
      fileOutputStream.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

// end of Level 2 methods
// end of Level 1 methods

  public final void writeExternal(final Element entry) {
    final Element items = new Element("Items");
    final Element classes = new Element("Classes");
    entry.getChildren().add(items);
    entry.getChildren().add(classes);
    for (AttributeGroup item : itemOrderAttributeList) {
      item.writeExternal(items);
    }
    for (AttributeGroup attributes : classOrderAttributeList) {
      attributes.writeExternal(classes);
    }
    final Element gsd = new Element("DefaultGetterSetterDefinition");
    entry.getChildren().add(gsd);
    defaultGSDefinition.writeExternal(gsd);
    final Element relatedItems = new Element("RelatedMethods");
    entry.getChildren().add(relatedItems);
    entry.setAttribute("KeepGettersSettersTogether", Boolean.valueOf(keepGettersSettersTogether).toString());
    entry.setAttribute("KeepGettersSettersWithProperty", Boolean.valueOf(keepGettersSettersWithProperty).toString());
    entry.setAttribute("KeepOverloadedMethodsTogether", Boolean.valueOf(keepOverloadedMethodsTogether).toString());
    entry.setAttribute("ConfirmBeforeRearranging", Boolean.valueOf(askBeforeRearranging).toString());
    entry.setAttribute("RearrangeInnerClasses", Boolean.valueOf(rearrangeInnerClasses).toString());
    entry.setAttribute("globalCommentPattern", globalCommentPattern);
    entry.setAttribute("overloadedOrder", "" + overloadedOrder);
    entry.setAttribute("ShowParameterTypes", Boolean.valueOf(showParameterTypes).toString());
    entry.setAttribute("ShowParameterNames", Boolean.valueOf(showParameterNames).toString());
    entry.setAttribute("ShowFields", Boolean.valueOf(showFields).toString());
    entry.setAttribute("ShowTypeAfterMethod", Boolean.valueOf(showTypeAfterMethod).toString());
    entry.setAttribute("ShowRules", Boolean.valueOf(showRules).toString());
    entry.setAttribute("ShowMatchedRules", Boolean.valueOf(showMatchedRules).toString());
    entry.setAttribute("ShowComments", Boolean.valueOf(showComments).toString());
    entry.setAttribute("RemoveBlanksInsideCodeBlocks", Boolean.valueOf(removeBlanksInsideCodeBlocks).toString());
    relatedMethodsSettings.writeExternal(relatedItems);
    afterClassLBrace.writeExternal(entry);
    afterClassRBrace.writeExternal(entry);
    beforeClassRBrace.writeExternal(entry);
    beforeMethodLBrace.writeExternal(entry);
    afterMethodLBrace.writeExternal(entry);
    afterMethodRBrace.writeExternal(entry);
    beforeMethodRBrace.writeExternal(entry);
    newlinesAtEOF.writeExternal(entry);
  }
}
