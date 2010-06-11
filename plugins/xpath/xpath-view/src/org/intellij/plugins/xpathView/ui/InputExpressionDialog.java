/*
 * Copyright 2007 Sascha Weinreuter
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
package org.intellij.plugins.xpathView.ui;

import org.intellij.plugins.xpathView.Config;
import org.intellij.plugins.xpathView.HistoryElement;
import org.intellij.plugins.xpathView.eval.EvalExpressionDialog;
import org.intellij.plugins.xpathView.support.XPathSupport;
import org.intellij.plugins.xpathView.util.Namespace;
import org.intellij.plugins.xpathView.util.NamespaceCollector;
import org.intellij.plugins.xpathView.util.Variable;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.EditorTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.context.AbstractVariableContext;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.context.ContextType;
import org.intellij.lang.xpath.context.NamespaceContext;
import org.intellij.lang.xpath.context.VariableContext;
import org.intellij.lang.xpath.psi.PrefixReference;
import org.intellij.lang.xpath.psi.QNameElement;
import org.intellij.lang.xpath.psi.XPathElement;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.xml.namespace.QName;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public abstract class InputExpressionDialog<FormType extends InputForm> extends ModeSwitchableDialog {
    protected final Project myProject;
    protected final FormType myForm;
    protected final Config mySettings;

    private final HistoryModel myModel;

    private final Document myDocument;
    private final MultilineEditor myEditor;

    private final EditorTextField myComboboxEditor;
    private final ComboBox myComboBox = new ComboBox();
    private JComponent myEditorComponent;

    private @Nullable Set<Namespace> myNamespaceCache;
    private InteractiveContextProvider myContextProvider;
    private final PsiFile myXPathFile;

    public InputExpressionDialog(final Project project, Config settings, final HistoryElement[] _history, FormType form) {
        super(project, false);

        myProject = project;
        myForm = form;

        setResizable(true);
        setModal(true);
        setHorizontalStretch(1.3f);

        mySettings = settings;

        myDocument = createXPathDocument(project, _history.length > 0 ? _history[_history.length - 1] : null);
        myXPathFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
        myModel = new HistoryModel(_history, myDocument);
        myEditor = new MultilineEditor(myDocument, project, XPathFileType.XPATH, myModel);
        myModel.addListDataListener(new ListDataListener() {
            final PsiDocumentManager docMgr = PsiDocumentManager.getInstance(project);
            final DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(project);

            public void intervalAdded(ListDataEvent e) {
            }

            public void intervalRemoved(ListDataEvent e) {
            }

            public void contentsChanged(ListDataEvent e) {
                final HistoryElement item = myModel.getSelectedItem();
                if (item != null) {
                    myContextProvider.getNamespaceContext().setMap(asMap(item.namespaces));
                    analyzer.restart();
                    analyzer.updateVisibleHighlighters(getEditor());
                }
            }
        });

        myComboboxEditor = new EditorTextField(myDocument, project, XPathFileType.XPATH);
        myComboBox.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                value = ((HistoryElement)value).expression;
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        });
        myComboBox.setModel(myModel);

        myComboBox.setEditor(new EditorAdapter(myComboboxEditor));
        myComboBox.setEditable(true);

        myDocument.addDocumentListener(new DocumentAdapter() {
            public void documentChanged(DocumentEvent e) {
                updateOkAction();
            }
        });

        init();
    }

    protected void init() {
        myForm.getIcon().setText(null);
        myForm.getIcon().setIcon(Messages.getQuestionIcon());

        myForm.getEditContextButton().addActionListener(new ActionListener() {

            @SuppressWarnings({"unchecked"})
            public void actionPerformed(ActionEvent e) {
                final HistoryElement selectedItem = myModel.getSelectedItem();

                final Collection<Namespace> n;
                final Collection<Variable> v;
                if (selectedItem != null) {
                    n = selectedItem.namespaces;
                    v = selectedItem.variables;
                } else {
                    n = Collections.EMPTY_SET;
                    v = Collections.EMPTY_SET;
                }

                // FIXME
                final Collection<Namespace> namespaces = myNamespaceCache != null ?
                        merge(myNamespaceCache, n, false) : n;

                final Set<String> unresolvedPrefixes = findUnresolvedPrefixes();
                final EditContextDialog dialog =
                        new EditContextDialog(myProject, unresolvedPrefixes, namespaces, v, myContextProvider);

                dialog.show();

                if (dialog.isOK()) {
                    final Pair<Collection<Namespace>, Collection<Variable>> context = dialog.getContext();
                    final Collection<Namespace> newNamespaces = context.getFirst();
                    final Collection<Variable> newVariables = context.getSecond();

                    updateContext(newNamespaces, newVariables);

                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            final Editor editor = getEditor();
                            if (editor != null) {
                                editor.getContentComponent().grabFocus();
                            }
                        }
                    });
                }
            }
        });

        updateOkAction();

        super.init();
    }

    void updateContext(Collection<Namespace> namespaces, Collection<Variable> variables) {
        final HistoryElement selectedItem = myModel.getSelectedItem();

        final HistoryElement newElement;
        if (selectedItem != null) {
            newElement = selectedItem.changeContext(namespaces, variables);
        } else {
            newElement = new HistoryElement(myDocument.getText(), variables, namespaces);
        }
        myModel.setSelectedItem(newElement);

        // FIXME
        if (myNamespaceCache == null) {
            myContextProvider.getNamespaceContext().setMap(asMap(namespaces));
        }

        final DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(myProject);
        analyzer.restart();
        analyzer.updateVisibleHighlighters(getEditor());
    }

    private Set<String> findUnresolvedPrefixes() {
        final Set<String> prefixes = new HashSet<String>();

        myXPathFile.accept(new PsiRecursiveElementVisitor(){
            public void visitElement(PsiElement element) {
                if (element instanceof QNameElement) {
                    final PsiReference[] references = element.getReferences();
                    for (PsiReference reference : references) {
                        if (reference instanceof PrefixReference) {
                            final PrefixReference prefixReference = ((PrefixReference)reference);
                            if (prefixReference.isUnresolved()) {
                                prefixes.add(prefixReference.getPrefix());
                            }
                        }
                    }
                }
                super.visitElement(element);
            }
        });
        return prefixes;
    }

    protected FormType getForm() {
        return myForm;
    }

    protected JComponent createCenterPanel() {
        return myForm.getComponent();
    }

    protected void updateOkAction() {
        getOKAction().setEnabled(isOkEnabled());
    }

    protected boolean isOkEnabled() {
        return myEditor.getDocument().getTextLength() > 0;
    }

    @Nullable
    protected Editor getEditor() {
        if (getMode() == Mode.ADVANCED) {
            return myEditor.getEditor();
        } else {
            return myComboboxEditor.getEditor();
        }
    }

    protected void setModeImpl(Mode mode) {
//        mySettingsPanel.setVisible(mode == Mode.ADVANCED);
        myForm.getEditContextButton().setVisible(mode == Mode.ADVANCED);

        if (mode == Mode.ADVANCED) {
            setEditor(myEditor, GridConstraints.SIZEPOLICY_WANT_GROW);
            myEditor.selectAll();
        } else {
            setEditor(myComboBox, GridConstraints.SIZEPOLICY_FIXED);
            myComboBox.setModel(myModel);
            myComboBox.getEditor().selectAll();
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                final Editor editor = getEditor();
                if (editor != null) {
                    editor.getContentComponent().grabFocus();
                }
            }
        });
    }

    private void setEditor(JComponent editor, int vSizePolicy) {
        if (myEditorComponent != null) {
            myForm.getEditorPanel().remove(myEditorComponent);
        }

        final GridConstraints gridConstraints = new GridConstraints();
        gridConstraints.setFill(vSizePolicy == GridConstraints.SIZEPOLICY_WANT_GROW ? GridConstraints.FILL_BOTH : GridConstraints.FILL_HORIZONTAL);
        gridConstraints.setVSizePolicy(vSizePolicy);
        myForm.getEditorPanel().add(myEditorComponent = editor, gridConstraints);
    }

    protected Document createXPathDocument(Project project, HistoryElement historyElement) {

        final String expression = historyElement != null ? historyElement.expression : "";
        final PsiFile file = PsiFileFactory.getInstance(project).createFileFromText("DummyFile.xpath", XPathFileType.XPATH, expression, LocalTimeCounter.currentTime(), true);
        final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        // not sure why this is required...
        assert document != null;
        document.setReadOnly(false);
        
        assert document.isWritable() : "WTF, document is not writable? Text = <" + expression + ">";
        return document;
    }

    @SuppressWarnings({ "unchecked" })
    public boolean show(XmlElement contextElement) {
        prepareShow(contextElement);

        show();

        return isOK();
    }

    @SuppressWarnings({"unchecked"})
    private void prepareShow(XmlElement contextElement) {

        final NamespaceCollector.CollectedInfo collectedInfo;
        if (contextElement != null) {
            collectedInfo = NamespaceCollector.collectInfo((XmlFile)contextElement.getContainingFile());
            myNamespaceCache = collectedInfo.namespaces;
        } else {
            collectedInfo = NamespaceCollector.empty();
            myNamespaceCache = null;
        }

        myContextProvider = new InteractiveContextProvider(contextElement, collectedInfo, myModel);
        myContextProvider.attachTo(myXPathFile);

        final HistoryElement historyElement = myModel.getSelectedItem();
        if (historyElement != null) {
            myContextProvider.getNamespaceContext().setMap(asMap(historyElement.namespaces));
        } else {
            myContextProvider.getNamespaceContext().setMap(asMap(null));
        }

        updateOkAction();
    }

    protected static Collection<Namespace> merge(Collection<Namespace> namespaces, Collection<Namespace> cache, boolean merge) {
        if (cache == null) return namespaces;

        final Set<Namespace> n;

        if (merge) {
            n = new HashSet<Namespace>(cache);
            n.removeAll(namespaces);
            n.addAll(namespaces);
        } else {
            n = new HashSet<Namespace>(namespaces);
            for (Namespace namespace : n) {
                for (Namespace cached : cache) {
                    if (namespace.getUri().equals(cached.getUri())) {
                        namespace.setPrefix(cached.prefix);
                    }
                }
            }
        }
        return n;
    }

    @SuppressWarnings({"unchecked"})
    protected Map<String, String> asMap(Collection<Namespace> namespaces) {
        if (namespaces == null) {
            if (myNamespaceCache != null) {
                return Namespace.makeMap(myNamespaceCache);
            } else {
                return Collections.EMPTY_MAP;
            }
        }

        if (this.myNamespaceCache != null) {
            namespaces = merge(myNamespaceCache, namespaces, false);
        }

        return Namespace.makeMap(namespaces);
    }

    public JComponent getPreferredFocusedComponent() {
        return myEditor;
    }

    @SuppressWarnings({"unchecked"})
    public Context getContext() {
        final HistoryElement context = myModel.getSelectedItem();
        if (context == null || context.expression == null) {
            final Set<Namespace> cache = myNamespaceCache != null ? myNamespaceCache : Collections.EMPTY_SET;
            return new Context(new HistoryElement(myDocument.getText(), Collections.EMPTY_SET, cache), getMode());
        }

        final Collection<Namespace> namespaces = myNamespaceCache != null ?
                merge(myNamespaceCache, context.namespaces, false) : context.namespaces;
        return new Context(new HistoryElement(context.expression, context.variables, namespaces), getMode());
    }

    public static class Context {
        public final HistoryElement input;
        public final Mode mode;

        Context(HistoryElement context, Mode mode) {
            this.input = context;
            this.mode = mode;
        }
    }

    protected class EditorAdapter extends BasicComboBoxEditor {
        private final EditorTextField myTf;

        public EditorAdapter(EditorTextField tf) {
            myTf = tf;
        }

        public Component getEditorComponent() {
            return myTf.getComponent();
        }

        @Nullable
        public Object getItem() {
            return myModel.getSelectedItem();
        }

        public void selectAll() {
            myTf.selectAll();
        }

        public void setItem(Object object) {
            if (object == null) {
                myEditor.setText("");
            } else {
                myEditor.setText(((HistoryElement)object).expression);
            }
        }
    }

    private static class MyVariableResolver extends AbstractVariableContext<String> {
        private final HistoryModel myModel;

        public MyVariableResolver(HistoryModel model) {
            myModel = model;
        }

        @NotNull
        public String[] getVariablesInScope(XPathElement element) {
            final HistoryElement selectedItem = myModel.getSelectedItem();
            if (selectedItem != null) {
                return Variable.asSet(selectedItem.variables).toArray(new String[selectedItem.variables.size()]);
            } else {
                return ArrayUtil.EMPTY_STRING_ARRAY;
            }
        }
    }

    private class InteractiveContextProvider extends ContextProvider {
        private final XmlElement myContextElement;
        private final NamespaceCollector.CollectedInfo myCollectedInfo;
        private final MyVariableResolver myVariableResolver;
        private final EvalExpressionDialog.MyNamespaceContext myNamespaceContext;

        public InteractiveContextProvider(XmlElement contextElement, NamespaceCollector.CollectedInfo collectedInfo, HistoryModel model) {
            myContextElement = contextElement;
            myCollectedInfo = collectedInfo;
            myVariableResolver = new MyVariableResolver(model);
            myNamespaceContext = new EvalExpressionDialog.MyNamespaceContext();
        }

        @NotNull
        public ContextType getContextType() {
            return XPathSupport.TYPE;
        }

        @Nullable
        public XmlElement getContextElement() {
            return myContextElement;
        }

        @NotNull
        public EvalExpressionDialog.MyNamespaceContext getNamespaceContext() {
            return myNamespaceContext;
        }

        public VariableContext getVariableContext() {
            return myVariableResolver;
        }

        public Set<QName> getAttributes(boolean forValidation) {
            return myCollectedInfo.attributes;
        }

        private Set<QName> filterDefaultNamespace(Set<QName> _set) {
            final Set<QName> set = new HashSet<QName>(_set);
            for (Iterator<QName> it = set.iterator(); it.hasNext();) {
                final QName name = it.next();
                final String prefix = name.getPrefix();
                if (prefix == null || prefix.length() == 0) {
                    final String uri = name.getNamespaceURI();
                    if (uri != null && uri.length() > 0) {
                        final String assignedPrefix = myNamespaceContext.getPrefixForURI(uri, null);
                        if (assignedPrefix == null || assignedPrefix.length() == 0) {
                            it.remove();
                        }
                    }
                }
            }
            return set;
        }

        public Set<QName> getElements(boolean forValidation) {
            return filterDefaultNamespace(myCollectedInfo.elements);
        }
    }

    protected class MyNamespaceContext implements NamespaceContext {
        private BidirectionalMap<String, String> myMap;

        @Nullable
        public String getNamespaceURI(String prefix, XmlElement context) {
            final String s = myMap.get(prefix);
            if (s == null && prefix.length() == 0) {
                return "";
            }
            return s;
        }

        @Nullable
        public String getPrefixForURI(String uri, XmlElement context) {
            final java.util.List<String> list = myMap.getKeysByValue(uri);
            return list != null && list.size() > 0 ? list.get(0) : null;
        }

        @NotNull
        public Collection<String> getKnownPrefixes(XmlElement context) {
            return myMap.keySet();
        }

        @Nullable
        public PsiElement resolve(String prefix, XmlElement context) {
            return null;
        }

        public void setMap(Map<String, String> map) {
            myMap = new BidirectionalMap<String, String>();
            myMap.putAll(map);
        }

        public IntentionAction[] getUnresolvedNamespaceFixes(PsiReference reference, String localName) {
            return new IntentionAction[]{ new MyRegisterPrefixAction(reference) };
        }
    }

    private class MyRegisterPrefixAction implements IntentionAction {
        private final PsiReference myReference;

        public MyRegisterPrefixAction(PsiReference reference) {
            myReference = reference;
        }

        @NotNull
        public String getText() {
            return "Register Namespace Prefix";
        }

        @NotNull
        public String getFamilyName() {
            return getText();
        }

        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return myReference instanceof PrefixReference && ((PrefixReference)myReference).isUnresolved();
        }

        public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            final Set<String> prefix = Collections.singleton(myReference.getCanonicalText());

            final Map<String, String> myMap = myContextProvider.getNamespaceContext().myMap;
            final Collection<String> list;
            if (myNamespaceCache == null) {
                final ExternalResourceManager erm = ExternalResourceManager.getInstance();
                list = new ArrayList<String>(Arrays.asList(erm.getResourceUrls(null, true)));
                for (String namespace : myMap.values()) {
                    list.remove(namespace);
                }
                Collections.sort((List<String>)list);
            } else {
                list = myMap.values();
            }

            final AddNamespaceDialog dlg = new AddNamespaceDialog(project, prefix, list, myNamespaceCache == null ?
                    AddNamespaceDialog.Mode.URI_EDITABLE :
                    AddNamespaceDialog.Mode.FIXED);

            dlg.show();

            if (dlg.isOK()) {
                final Namespace namespace = new Namespace(dlg.getPrefix(), dlg.getURI());

                final HistoryElement selectedItem = myModel.getSelectedItem();
                final Collection<Namespace> n;
                final Collection<Variable> v;
                if (selectedItem != null) {
                    n = new HashSet<Namespace>(selectedItem.namespaces);
                    n.remove(namespace);
                    n.add(namespace);
                    v = selectedItem.variables;
                } else {
                    n = Collections.singleton(namespace);
                    //noinspection unchecked
                    v = Collections.EMPTY_SET;
                }

                updateContext(n, v);
            }
        }

        public boolean startInWriteAction() {
            return false;
        }
    }
}
