package com.intellij.ide.plugins;

import org.xml.sax.SAXException;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Mar 28, 2003
 * Time: 12:57:45 AM
 * To change this template use Options | File Templates.
 */
class RepositoryContentHandler extends DefaultHandler {
  public static final String CATEGORY = "category";
  public static final String IDEA_PLUGIN = "idea-plugin";
  public static final String NAME = "name";
  public static final String DESCRIPTION = "description";
  public static final String VERSION = "version";
  public static final String VENDOR = "vendor";
  public static final String EMAIL = "email";
  public static final String URL = "url";
  public static final String IDEA_VERSION = "idea-version";
  public static final String SINCE_BUILD = "since-build";
  public static final String CHNAGE_NOTES = "change-notes";
  private static final String DEPENDS = "depends";

  private CategoryNode currentCategory;
  private PluginNode currentPlugin;
  private String currentValue;

  public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
    /*
    if (systemId != null && systemId.equals(RepositoryHelper.REPOSITORY_LIST_SYSTEM_ID)) {
      String location = ExternalResourceManager.getInstance().getResourceLocation(systemId);
      try {
        return new InputSource (new java.net.URL (location).openStream());
      }
      catch (IOException e) {
        return super.resolveEntity(publicId, systemId);
      }
    } else
    */
    //try {
      return super.resolveEntity(publicId, systemId);
    //}
    //catch (IOException e) {
    //  throw new SAXException(e);
    //}
  }

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
      currentPlugin.setDownloads(atts.getValue("downloads"));
      currentPlugin.setSize(atts.getValue("size"));
      currentPlugin.setUrl (atts.getValue("url"));
      currentPlugin.setDate (atts.getValue("date"));
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
    if (qName.equals(NAME))
      currentPlugin.setName(currentValue);
    else if (qName.equals(DESCRIPTION))
      currentPlugin.setDescription(currentValue);
    else if (qName.equals(VERSION))
      currentPlugin.setVersion(currentValue);
    else if (qName.equals(VENDOR)) {
      currentPlugin.setVendor(currentValue);
    } else if (qName.equals(DEPENDS)) {
      currentPlugin.addDepends(currentValue);
    } else if (qName.equals(CHNAGE_NOTES))
      currentPlugin.setChangeNotes(currentValue);
    else if (qName.equals(CATEGORY))
      currentCategory = (CategoryNode) currentCategory.getParent();
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
