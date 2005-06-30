package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.IllegalDataException;
import org.jdom.JDOMException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;


public class TemplateSettings implements JDOMExternalizable, ExportableApplicationComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.impl.TemplateSettings");

  public static final String USER_GROUP_NAME = "user";
  private static final String TEMPLATE_SET = "templateSet";
  private static final String GROUP = "group";
  private static final String TEMPLATE = "template";

  private static final String DELETED_TEMPLATES = "deleted_templates";
  private List<String> myDeletedTemplates = new ArrayList<String>();

  private static final String[] DEFAULT_TEMPLATES = new String[]{
    "/liveTemplates/html_xml",
    "/liveTemplates/iterations",
    "/liveTemplates/other",
    "/liveTemplates/output",
    "/liveTemplates/plain",
    "/liveTemplates/surround"
  };

  public static final char SPACE_CHAR = ' ';
  public static final char TAB_CHAR = '\t';
  public static final char ENTER_CHAR = '\n';
  public static final char DEFAULT_CHAR = 'D';

  private static final String SPACE = "SPACE";
  private static final String TAB = "TAB";
  private static final String ENTER = "ENTER";

  private static final String NAME = "name";
  private static final String VALUE = "value";
  private static final String DESCRIPTION = "description";
  private static final String SHORTCUT = "shortcut";

  private static final String VARIABLE = "variable";
  private static final String EXPRESSION = "expression";
  private static final String DEFAULT_VALUE = "defaultValue";
  private static final String ALWAYS_STOP_AT = "alwaysStopAt";

  private static final String CONTEXT = "context";
  private static final String TO_REFORMAT = "toReformat";
  private static final String TO_SHORTEN_FQ_NAMES = "toShortenFQNames";

  private static final String DEFAULT_SHORTCUT = "defaultShortcut";
  private String DEACTIVATED = "deactivated";

  private Map myTemplates = new LinkedHashMap();
  private Map myDefaultTemplates = new LinkedHashMap();
  private int myMaxKeyLength = 0;
  private char myDefaultShortcutChar = TAB_CHAR;
  private String myLastSelectedTemplateKey;

  public TemplateSettings(Application application) {
    loadTemplates(application);
  }

  public File[] getExportFiles() {
    return new File[]{getTemplateDirectory(true),PathManager.getDefaultOptionsFile()};
  }

  public String getPresentableName() {
    return "Code templates";
  }

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public static TemplateSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(TemplateSettings.class);
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    Element element = parentNode.getChild(DEFAULT_SHORTCUT);
    if (element != null) {
      String shortcut = element.getAttributeValue(SHORTCUT);
      if (TAB.equals(shortcut)) {
        myDefaultShortcutChar = TAB_CHAR;
      } else if (ENTER.equals(shortcut)) {
        myDefaultShortcutChar = ENTER_CHAR;
      } else {
        myDefaultShortcutChar = SPACE_CHAR;
      }
    }

    Element deleted = parentNode.getChild(DELETED_TEMPLATES);
    if (deleted != null) {
      List children = deleted.getChildren();
      for (final Object aChildren : children) {
        Element child = (Element)aChildren;
        myDeletedTemplates.add(child.getAttributeValue(NAME));
      }
    }

    loadTemplates(ApplicationManager.getApplication());
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    Element element = new Element(DEFAULT_SHORTCUT);
    if (myDefaultShortcutChar == TAB_CHAR) {
      element.setAttribute(SHORTCUT, TAB);
    } else if (myDefaultShortcutChar == ENTER_CHAR) {
      element.setAttribute(SHORTCUT, ENTER);
    } else {
      element.setAttribute(SHORTCUT, SPACE);
    }
    parentNode.addContent(element);

    if (myDeletedTemplates.size() > 0) {
      Element deleted = new Element(DELETED_TEMPLATES);
      for (final String myDeletedTemplate : myDeletedTemplates) {
        Element template = new Element(TEMPLATE);
        template.setAttribute(NAME, (String)myDeletedTemplate);
        deleted.addContent(template);

      }
      parentNode.addContent(deleted);
    }
  }

  public String getLastSelectedTemplateKey() {
    return myLastSelectedTemplateKey;
  }

  public void setLastSelectedTemplateKey(String key) {
    myLastSelectedTemplateKey = key;
  }

  public TemplateImpl[] getTemplates() {
    return (TemplateImpl[]) myTemplates.values().toArray(new TemplateImpl[myTemplates.size()]);
  }

  public char getDefaultShortcutChar() {
    return myDefaultShortcutChar;
  }

  public void setDefaultShortcutChar(char defaultShortcutChar) {
    myDefaultShortcutChar = defaultShortcutChar;
  }

  public TemplateImpl getTemplate(String key) {
    return (TemplateImpl) myTemplates.get(key);
  }

  public int getMaxKeyLength() {
    return myMaxKeyLength;
  }

  public void setTemplates(TemplateImpl[] newTemplates) {
    myTemplates.clear();
    myMaxKeyLength = 0;
    for (TemplateImpl template : newTemplates) {
      myTemplates.put(template.getKey(), template);
      myMaxKeyLength = Math.max(myMaxKeyLength, template.getKey().length());
    }

    saveTemplates(newTemplates);
  }

  public void addTemplate(Template template) {
    myTemplates.put(template.getKey(), template);
    myMaxKeyLength = Math.max(myMaxKeyLength, template.getKey().length());
    saveTemplates(getTemplates());
  }

  private TemplateImpl addTemplate(String key, String string, String group, String description, String shortcut, boolean isDefault) {
    TemplateImpl template = new TemplateImpl(key, string, group);
    template.setDescription(description);
    if (TAB.equals(shortcut)) {
      template.setShortcutChar(TAB_CHAR);
    } else if (ENTER.equals(shortcut)) {
      template.setShortcutChar(ENTER_CHAR);
    } else if (SPACE.equals(shortcut)) {
      template.setShortcutChar(SPACE_CHAR);
    } else {
      template.setShortcutChar(DEFAULT_CHAR);
    }
    if (isDefault) {
      myDefaultTemplates.put(key, template);
      if (myTemplates.get(key) != null) return template;
    }
    myTemplates.put(key, template);
    myMaxKeyLength = Math.max(myMaxKeyLength, key.length());
    return template;
  }

  private static File getTemplateDirectory(boolean toCreate) {
    String directoryPath = PathManager.getConfigPath() + File.separator + "templates";
    File directory = new File(directoryPath);
    if (!directory.exists()) {
      if (!toCreate) {
        return null;
      }
      if (!directory.mkdir()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("cannot create directory: " + directory.getAbsolutePath());
        }
        return null;
      }
    }
    return directory;
  }

  private static File[] getUserTemplateFiles() {
    File directory = getTemplateDirectory(false);
    if (directory == null || !directory.exists()) {
      directory = getTemplateDirectory(true);
    }
    return directory.listFiles();
  }

  private void loadTemplates(Application application) {
    File[] files = getUserTemplateFiles();
    if (files == null) {
      return;
    }

    try {
      for (File file : files) {
        String name = file.getName();
        if (!name.toLowerCase().endsWith(".xml")) continue;
        readTemplateFile(file);
      }

      for (String defTemplate : DEFAULT_TEMPLATES) {
        String templateName = getDefaultTemplateName(defTemplate);
        readDefTemplateFile(DecodeDefaultsUtil.getDefaultsInputStream(this, defTemplate), templateName);
      }
    } catch (Exception e) {
      LOG.error(e);
    }
  }

  private String getDefaultTemplateName(String defTemplate) {
    return defTemplate.substring(defTemplate.lastIndexOf("/") + 1);
  }

  private void readDefTemplateFile(InputStream inputStream, String defGroupName) throws JDOMException, InvalidDataException, IOException {
    readTemplateFile(JDOMUtil.loadDocument(inputStream), defGroupName, true);
  }

  private void readTemplateFile(File file) throws JDOMException, InvalidDataException, IOException {
    if (!file.exists()) return;

    String defGroupName = FileUtil.getNameWithoutExtension(file);
    readTemplateFile(JDOMUtil.loadDocument(file), defGroupName, false);
  }

  private void readTemplateFile(Document document, String defGroupName, boolean isDefault) throws InvalidDataException {
    if (document == null) {
      throw new InvalidDataException();
    }
    Element root = document.getRootElement();
    if (root == null || !TEMPLATE_SET.equals(root.getName())) {
      throw new InvalidDataException();
    }

    String groupName = root.getAttributeValue(GROUP);
    if (groupName == null || groupName.length() == 0) groupName = defGroupName;

    for (final Object o1 : root.getChildren(TEMPLATE)) {
      Element element = (Element)o1;

      String name = element.getAttributeValue(NAME);
      String value = element.getAttributeValue(VALUE);
      String description = element.getAttributeValue(DESCRIPTION);
      String shortcut = element.getAttributeValue(SHORTCUT);
      if (isDefault && myDeletedTemplates.contains(name)) continue;
      TemplateImpl template = addTemplate(name, value, groupName, description, shortcut, isDefault);
      template.setToReformat("true".equals(element.getAttributeValue(TO_REFORMAT)));
      template.setToShortenLongNames("true".equals(element.getAttributeValue(TO_SHORTEN_FQ_NAMES)));
      template.setDeactivated("true".equals(element.getAttributeValue(DEACTIVATED)));


      for (final Object o : element.getChildren(VARIABLE)) {
        Element e = (Element)o;
        String variableName = e.getAttributeValue(NAME);
        String expression = e.getAttributeValue(EXPRESSION);
        String defaultValue = e.getAttributeValue(DEFAULT_VALUE);
        boolean isAlwaysStopAt = "true".equals(e.getAttributeValue(ALWAYS_STOP_AT));
        template.addVariable(variableName, expression, defaultValue, isAlwaysStopAt);
      }

      Element context = element.getChild(CONTEXT);
      if (context != null) {
        DefaultJDOMExternalizer.readExternal(template.getTemplateContext(), context);
      }
    }
  }

  private void saveTemplates(final TemplateImpl[] templates) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        List templateNames = new ArrayList();
        for (int i = 0; i < templates.length; i++) {
          templateNames.add(templates[i].getKey());
        }
        myDeletedTemplates.clear();
        for (Iterator it = myDefaultTemplates.keySet().iterator(); it.hasNext();) {
          String defTemplateName = (String) it.next();
          if (!templateNames.contains(defTemplateName)) {
            myDeletedTemplates.add(defTemplateName);
          }
        }

        File[] files = getUserTemplateFiles();
        if (files != null) {
          for (int i = 0; i < files.length; i++) {
            files[i].delete();
          }
        }

        if (templates.length == 0) return;
        com.intellij.util.containers.HashMap groupToDocumentMap = new com.intellij.util.containers.HashMap();
        for (int i = 0; i < templates.length; i++) {
          TemplateImpl template = templates[i];
          if (template.equals(myDefaultTemplates.get(template.getKey()))) continue;

          String groupName = templates[i].getGroupName();
          Element templateSetElement = (Element) groupToDocumentMap.get(groupName);
          if (templateSetElement == null) {
            templateSetElement = new Element(TEMPLATE_SET);
            templateSetElement.setAttribute(GROUP, groupName);
            groupToDocumentMap.put(groupName, templateSetElement);
          }
          try {
            saveTemplate(template, templateSetElement);
          } catch (IllegalDataException e) {
          }
        }

        File dir = getTemplateDirectory(true);
        if (dir == null) {
          return;
        }

        Collection groups = groupToDocumentMap.entrySet();
        for (Iterator it = groups.iterator(); it.hasNext();) {
          Map.Entry entry = (Map.Entry) it.next();
          String groupName = (String) entry.getKey();
          Element templateSetElement = (Element) entry.getValue();

          String fileName = convertName(groupName);
          String filePath = findFirstNotExistingFile(dir, fileName, ".xml");
          try {
            JDOMUtil.writeDocument(new Document(templateSetElement), filePath, CodeStyleSettingsManager.getSettings(null).getLineSeparator());
          } catch (IOException e) {
            LOG.error(e);
          }
        }
      }
    });
  }

  private void saveTemplate(TemplateImpl template, Element templateSetElement) {
    Element element = new Element(TEMPLATE);
    element.setAttribute(NAME, template.getKey());
    element.setAttribute(VALUE, template.getString());
    if (template.getShortcutChar() == TAB_CHAR) {
      element.setAttribute(SHORTCUT, TAB);
    } else if (template.getShortcutChar() == ENTER_CHAR) {
      element.setAttribute(SHORTCUT, ENTER);
    } else if (template.getShortcutChar() == SPACE_CHAR) {
      element.setAttribute(SHORTCUT, SPACE);
    }
    if (template.getDescription() != null) {
      element.setAttribute(DESCRIPTION, template.getDescription());
    }
    element.setAttribute(TO_REFORMAT, template.isToReformat() ? "true" : "false");
    element.setAttribute(TO_SHORTEN_FQ_NAMES, template.isToShortenLongNames() ? "true" : "false");
    if (template.isDeactivated()) {
      element.setAttribute(DEACTIVATED, "true");
    }

    for (int i = 0; i < template.getVariableCount(); i++) {
      Element variableElement = new Element(VARIABLE);
      variableElement.setAttribute(NAME, template.getVariableNameAt(i));
      variableElement.setAttribute(EXPRESSION, template.getExpressionStringAt(i));
      variableElement.setAttribute(DEFAULT_VALUE, template.getDefaultValueStringAt(i));
      variableElement.setAttribute(ALWAYS_STOP_AT, template.isAlwaysStopAt(i) ? "true" : "false");
      element.addContent(variableElement);
    }

    try {
      Element contextElement = new Element(CONTEXT);
      DefaultJDOMExternalizer.writeExternal(template.getTemplateContext(), contextElement);
      element.addContent(contextElement);
    } catch (WriteExternalException e) {
    }
    templateSetElement.addContent(element);
  }

  private String findFirstNotExistingFile(File directory, String fileName, String extension) {
    String filePath = directory.getAbsolutePath() + File.separator + fileName + extension;
    File file = new File(filePath);
    if (!file.exists()) {
      return filePath;
    }
    for (int i = 1; ; i++) {
      filePath = directory.getAbsolutePath() + File.separator + fileName + i + extension;
      file = new File(filePath);
      if (!file.exists()) {
        return filePath;
      }
    }
  }


  private String convertName(String s) {
    if (s == null || s.length() == 0) {
      return "_";
    }
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isJavaIdentifierPart(c) || c == ' ') {
        buf.append(c);
      } else {
        buf.append('_');
      }
    }
    return buf.toString();
  }

  public String getComponentName() {
    return "TemplateSettings";
  }

}