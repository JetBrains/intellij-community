package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Mar 28, 2003
 * Time: 12:57:45 AM
 * To change this template use Options | File Templates.
 */
class RepositoryContentHandler extends DefaultHandler {
  @NonNls public static final String CATEGORY = "category";
  @NonNls public static final String IDEA_PLUGIN = "idea-plugin";
  @NonNls public static final String NAME = "name";
  @NonNls public static final String ID = "id";
  @NonNls public static final String DESCRIPTION = "description";
  @NonNls public static final String VERSION = "version";
  @NonNls public static final String VENDOR = "vendor";
  @NonNls public static final String EMAIL = "email";
  @NonNls public static final String URL = "url";
  @NonNls public static final String IDEA_VERSION = "idea-version";
  @NonNls public static final String SINCE_BUILD = "since-build";
  @NonNls public static final String CHNAGE_NOTES = "change-notes";
  @NonNls private static final String DEPENDS = "depends";
  @NonNls private static final String DOWNLOADS = "downloads";
  @NonNls private static final String SIZE = "size";
  @NonNls private static final String DATE = "date";

  private CategoryNode currentCategory;
  private PluginNode currentPlugin;
  private String currentValue;


  public void startDocument()
    throws SAXException {
    currentCategory = new CategoryNode();
    currentCategory .setName("");
  }

  public void startElement(String namespaceURI, String localName,
                           String qName, Attributes atts)
    throws SAXException {
    if (qName.equals(CATEGORY)) {
      CategoryNode categoryNode = new CategoryNode();
      categoryNode.setName (atts.getValue(NAME));
      categoryNode.setParent(currentCategory);

      currentCategory.addChild(categoryNode);
      currentCategory = categoryNode;
    } else if (qName.equals(IDEA_PLUGIN)) {
      currentPlugin = new PluginNode();
      currentPlugin.setParent(currentCategory);
      currentPlugin.setDownloads(atts.getValue(DOWNLOADS));
      currentPlugin.setSize(atts.getValue(SIZE));
      currentPlugin.setUrl (atts.getValue(URL));
      currentPlugin.setDate (atts.getValue(DATE));
      currentCategory.addPlugin(currentPlugin);
    } else if (qName.equals(IDEA_VERSION)) {
      currentPlugin.setSinceBuild(atts.getValue(SINCE_BUILD));
    } else if (qName.equals(VENDOR)) {
      currentPlugin.setVendorEmail(atts.getValue(EMAIL));
      currentPlugin.setVendorUrl(atts.getValue(URL));
    }
    currentValue = "";
  }

  public void endElement(String namespaceURI, String localName,
                         String qName)
    throws SAXException {
    if (qName.equals(ID)) {
      currentPlugin.setId(currentValue);
    }
    else if (qName.equals(NAME)) {
      currentPlugin.setName(currentValue);
    }
    else if (qName.equals(DESCRIPTION)) {
      currentPlugin.setDescription(currentValue);
    }
    else if (qName.equals(VERSION)) {
      currentPlugin.setVersion(currentValue);
    }
    else if (qName.equals(VENDOR)) {
      currentPlugin.setVendor(currentValue);
    }
    else if (qName.equals(DEPENDS)) {
      currentPlugin.addDepends(PluginId.getId(currentValue));
    }
    else if (qName.equals(CHNAGE_NOTES)) {
      currentPlugin.setChangeNotes(currentValue);
    }
    else if (qName.equals(CATEGORY)) {
      currentCategory = (CategoryNode)currentCategory.getParent();
    }
    currentValue = "";
  }

  public void characters(char ch[], int start, int length)
    throws SAXException {
    currentValue += new String (ch, start, length);
  }

  public CategoryNode getRoot() {
    return currentCategory;
  }
}
