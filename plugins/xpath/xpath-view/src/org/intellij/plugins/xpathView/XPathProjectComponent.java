/*
 * Copyright 2002-2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.xpathView;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import org.intellij.plugins.xpathView.util.Namespace;
import org.intellij.plugins.xpathView.util.Variable;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Project component.<br>
 * Keeps track of history, etc. on project-basis.<br>
 * It is configured to write its settings into the .iws file rather
 * than the .ipr file. (see plugin.xml)
 */
@State(name = "XPathView.XPathProjectComponent", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class XPathProjectComponent implements PersistentStateComponent<Element> {
    protected static final String HISTORY_ELEMENT = "element";
    protected static final String HISTORY = "history";
    protected static final String FIND_HISTORY = "find-history";
    protected static final String EXPRESSION = "expression";
    protected static final String VARIABLE = "variable";
    protected static final String NAME = "name";
    protected static final String NAMESPACE = "namespace";
    protected static final String PREFIX = "prefix";
    protected static final String URI = "uri";

    /** A set that maintains the history */
    private final LinkedHashMap<String, HistoryElement> history = new LinkedHashMap<>();
    private final LinkedHashMap<String, HistoryElement> findHistory = new LinkedHashMap<>();

//    private Set<Namespace> namespaces = new HashSet();

  @Override
  public void loadState(@NotNull Element state) {
    readHistory(state, HISTORY, history);
    readHistory(state, FIND_HISTORY, findHistory);
  }

    private static void readHistory(Element element, String s, LinkedHashMap<String, HistoryElement> hst) {
        final Element historyElement = element.getChild(s);
        if (historyElement != null) {
            final List<Element> entries = historyElement.getChildren(HISTORY_ELEMENT);
            for (final Element entry : entries) {
                final String expression = entry.getAttributeValue(EXPRESSION);
                if (expression != null) {
                    List<Element> children = entry.getChildren(VARIABLE);
                    final Collection<Variable> variables = new ArrayList<>(children.size());
                    for (Element e : children) {
                        variables.add(new Variable(e.getAttributeValue(NAME), e.getAttributeValue(EXPRESSION)));
                    }

                    children = entry.getChildren(NAMESPACE);
                    final Collection<Namespace> namespaces = new ArrayList<>(children.size());
                    for (Element namespaceElement : children) {
                        namespaces.add(new Namespace(namespaceElement.getAttributeValue(PREFIX), namespaceElement.getAttributeValue(URI)));
                    }
                    hst.put(expression, new HistoryElement(expression, variables, namespaces));
                }
            }
        }
    }

  @Override
  public Element getState() {
    Element element = new Element("xpathview");
    writeHistory(element, HISTORY, history);
    writeHistory(element, FIND_HISTORY, findHistory);
    return element;
  }

    private static void writeHistory(Element element, String s, LinkedHashMap<String, HistoryElement> hst) {
        final Element historyElement = new Element(s);
        element.addContent(historyElement);

        for (String key : hst.keySet()) {
            final Element entryElement = new Element(HISTORY_ELEMENT);
            entryElement.setAttribute(EXPRESSION, key);
            historyElement.addContent(entryElement);

            final HistoryElement h = hst.get(key);
            for (Variable variable : h.variables) {
                final Element varElement = new Element(VARIABLE);
                varElement.setAttribute(NAME, variable.getName());
                varElement.setAttribute(EXPRESSION, variable.getExpression());
                entryElement.addContent(varElement);
            }

            for (Namespace namespace : h.namespaces) {
                final Element namespaceElement = new Element(NAMESPACE);
                namespaceElement.setAttribute(PREFIX, namespace.getPrefix());
                namespaceElement.setAttribute(URI, namespace.getUri());
                entryElement.addContent(namespaceElement);
            }
        }
    }

    /**
     * Add a string to the history list
     */
    public void addHistory(HistoryElement element) {
      String expression = element.expression;
      history.remove(expression);
      history.put(expression, element);
    }

  public void addFindHistory(HistoryElement element) {
    String expression = element.expression;
    findHistory.remove(expression);
    findHistory.put(expression, element);
  }

    /**
     * Returns the history
     * @return the history as an array of strings
     */
    public HistoryElement[] getHistory() {
        return history.values().toArray(new HistoryElement[0]);
    }

    public HistoryElement[] getFindHistory() {
        return findHistory.values().toArray(new HistoryElement[0]);
    }

    public static XPathProjectComponent getInstance(Project project) {
      return project.getService(XPathProjectComponent.class);
    }
}
