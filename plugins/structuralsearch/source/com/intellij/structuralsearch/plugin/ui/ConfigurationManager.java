package com.intellij.structuralsearch.plugin.ui;

import org.jdom.Element;
import org.jdom.Attribute;

import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Collection;

import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 10.02.2004
 * Time: 14:29:45
 * To change this template use File | Settings | File Templates.
 */
public class ConfigurationManager {
  private static final String SAVE_TAG_NAME = "searchConfiguration";
  private static final String SAVE_TAG_NAME2 = "replaceConfiguration";
  private static final String SAVE_HISTORY_ATTR_NAME = "history";

  private List configurations;
  private LinkedList historyConfigurations;

  public void addHistoryConfiguration(Configuration configuration) {
    if (historyConfigurations == null) historyConfigurations = new LinkedList();

    if (historyConfigurations.indexOf(configuration)==-1) {
      historyConfigurations.addFirst(configuration);
    }
  }

  public void removeHistoryConfiguration(Configuration configuration) {
    if (historyConfigurations!=null) {
      historyConfigurations.remove(configuration);
    }
  }

  public void addConfiguration(Configuration configuration) {
    if (configurations == null) configurations = new LinkedList();

    if (configurations.indexOf(configuration)==-1) {
      configurations.add( configuration );
    }
  }

  public void removeConfiguration(Configuration configuration) {
    if (configurations!=null) {
      configurations.remove(configuration);
    }
  }

  public void saveConfigurations(Element element) {
    if (configurations!=null) {
      for(Iterator i = configurations.iterator();i.hasNext();) {
        saveOneConfiguration(element, (Configuration)i.next());
      }
    }

    if (historyConfigurations!=null) {
      for(Iterator i = historyConfigurations.iterator();i.hasNext();) {
        final Element infoElement = saveOneConfiguration(element, (Configuration)i.next());

        infoElement.getAttributes().add(
          new Attribute(SAVE_HISTORY_ATTR_NAME,"1")
        );
      }
    }
  }

  private static Element saveOneConfiguration(Element element, final Configuration config) {
    Element infoElement;

    element.getChildren().add(
      infoElement = new Element( config instanceof SearchConfiguration ? SAVE_TAG_NAME:SAVE_TAG_NAME2)
    );
    config.writeExternal(infoElement);

    return infoElement;
  }

  public void loadConfigurations(Element element) {
    if (configurations != null) return;
    List patterns = element.getChildren(SAVE_TAG_NAME);

    if (patterns!=null && patterns.size() > 0) {
      for(Iterator i = patterns.iterator();i.hasNext();) {
        final Configuration config = new SearchConfiguration();
        final Element childElement = (Element)i.next();

        config.readExternal( childElement );

        if (childElement.getAttribute(SAVE_HISTORY_ATTR_NAME)!=null) {
          if (historyConfigurations==null) historyConfigurations = new LinkedList();
          historyConfigurations.add(config);
        } else {
          addConfiguration( config );
        }
      }
    }

    patterns = element.getChildren(SAVE_TAG_NAME2);

    if (patterns!=null && patterns.size() > 0) {
      for(Iterator i = patterns.iterator();i.hasNext();) {
        final Configuration config = new ReplaceConfiguration();

        config.readExternal( (Element)i.next() );
        addConfiguration( config );
      }
    }
  }

  public Collection getConfigurations() {
    return configurations;
  }

  public Collection getHistoryConfigurations() {
    return historyConfigurations;
  }
}
