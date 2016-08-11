/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.xml.ui;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeInsight.daemon.impl.DefaultHighlightInfoProcessor;
import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass;
import com.intellij.codeInsight.daemon.impl.LocalInspectionsPass;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.BooleanTableCellEditor;
import com.intellij.ui.UserActivityListener;
import com.intellij.ui.UserActivityWatcher;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ClassMap;
import com.intellij.util.ui.JBUI;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManagerImpl;
import com.intellij.util.xml.highlighting.DomElementsErrorPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.lang.reflect.Type;

/**
 * @author peter
 */
public class DomUIFactoryImpl extends DomUIFactory {

  private final ClassMap<Function<DomWrapper<String>, BaseControl>> myCustomControlCreators = new ClassMap<>();
  private final ClassMap<Function<DomElement, TableCellEditor>> myCustomCellEditorCreators = new ClassMap<>();

  public DomUIFactoryImpl() {
    final Function<DomElement, TableCellEditor> booleanCreator = domElement -> new BooleanTableCellEditor();
    registerCustomCellEditor(Boolean.class, booleanCreator);
    registerCustomCellEditor(boolean.class, booleanCreator);
    registerCustomCellEditor(String.class, domElement -> new DefaultCellEditor(removeBorder(new JTextField())));
    Consumer<DomUIFactory>[] extensions = Extensions.getExtensions(EXTENSION_POINT_NAME);
    for (Consumer<DomUIFactory> extension : extensions) {
      extension.consume(this);
    }
  }

  @Override
  protected TableCellEditor createCellEditor(DomElement element, Class type) {
    if (Enum.class.isAssignableFrom(type)) {
      return new ComboTableCellEditor((Class<? extends Enum>)type, false);
    }

    final Function<DomElement, TableCellEditor> function = myCustomCellEditorCreators.get(type);
    assert function != null : "Type not supported: " + type;
    return function.fun(element);
  }

  @Override
  public final UserActivityWatcher createEditorAwareUserActivityWatcher() {
    return new UserActivityWatcher() {
      private final DocumentAdapter myListener = new DocumentAdapter() {
        @Override
        public void documentChanged(DocumentEvent e) {
          fireUIChanged();
        }
      };

      @Override
      protected void processComponent(final Component component) {
        super.processComponent(component);
        if (component instanceof EditorComponentImpl) {
          ((EditorComponentImpl)component).getEditor().getDocument().addDocumentListener(myListener);
        }
      }

      @Override
      protected void unprocessComponent(final Component component) {
        super.unprocessComponent(component);
        if (component instanceof EditorComponentImpl) {
          ((EditorComponentImpl)component).getEditor().getDocument().removeDocumentListener(myListener);
        }
      }
    };
  }

  @Override
  public void setupErrorOutdatingUserActivityWatcher(final CommittablePanel panel, final DomElement... elements) {
    final UserActivityWatcher userActivityWatcher = createEditorAwareUserActivityWatcher();
    userActivityWatcher.addUserActivityListener(new UserActivityListener() {
      private boolean isProcessingChange;

      @Override
      public void stateChanged() {
        if (isProcessingChange) return;
        isProcessingChange = true;
        try {
          for (final DomElement element : elements) {
            DomElementAnnotationsManagerImpl.outdateProblemHolder(element);
          }
          CommittableUtil.updateHighlighting(panel);
        }
        finally {
          isProcessingChange = false;
        }
      }
    }, panel);
    userActivityWatcher.register(panel.getComponent());
  }

  @Override
  @Nullable
  public BaseControl createCustomControl(final Type type, DomWrapper<String> wrapper, final boolean commitOnEveryChange) {
    final Function<DomWrapper<String>, BaseControl> factory = myCustomControlCreators.get(ReflectionUtil.getRawType(type));
    return factory == null ? null : factory.fun(wrapper);
  }

  @Override
  public CaptionComponent addErrorPanel(CaptionComponent captionComponent, DomElement... elements) {
    captionComponent.initErrorPanel(new DomElementsErrorPanel(elements));
    return captionComponent;
  }

  @Override
  public BackgroundEditorHighlighter createDomHighlighter(final Project project, final PerspectiveFileEditor editor, final DomElement element) {
    return new BackgroundEditorHighlighter() {
      @Override
      @NotNull
      public HighlightingPass[] createPassesForEditor() {
        if (!element.isValid()) return HighlightingPass.EMPTY_ARRAY;
        
        final XmlFile psiFile = DomUtil.getFile(element);

        final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        final Document document = psiDocumentManager.getDocument(psiFile);
        if (document == null) return HighlightingPass.EMPTY_ARRAY;

        editor.commit();

        psiDocumentManager.commitAllDocuments();

        GeneralHighlightingPass ghp = new GeneralHighlightingPass(project, psiFile, document, 0, document.getTextLength(),
                                                                  true, new ProperTextRange(0, document.getTextLength()), null, new DefaultHighlightInfoProcessor());
        LocalInspectionsPass lip = new LocalInspectionsPass(psiFile, document, 0,
                                                            document.getTextLength(), LocalInspectionsPass.EMPTY_PRIORITY_RANGE, true,
                                                            new DefaultHighlightInfoProcessor());
        return new HighlightingPass[]{ghp, lip};
      }

      @Override
      @NotNull
      public HighlightingPass[] createPassesForVisibleArea() {
        return createPassesForEditor();
      }
    };

  }

  @Override
  public BaseControl createTextControl(DomWrapper<String> wrapper, final boolean commitOnEveryChange) {
    return new TextControl(wrapper, commitOnEveryChange);
  }

  @Override
  public void registerCustomControl(@NotNull Class aClass, Function<DomWrapper<String>, BaseControl> creator) {
    myCustomControlCreators.put(aClass, creator);
  }

  @Override
  public void registerCustomCellEditor(@NotNull final Class aClass, final Function<DomElement, TableCellEditor> creator) {
    myCustomCellEditorCreators.put(aClass, creator);
  }

  private static <T extends JComponent> T removeBorder(final T component) {
    component.setBorder(JBUI.Borders.empty());
    return component;
  }
}
