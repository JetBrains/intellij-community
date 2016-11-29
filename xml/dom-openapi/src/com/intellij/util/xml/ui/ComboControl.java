/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;
import com.intellij.util.xml.highlighting.DomElementsProblemsHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;

/**
 * @author peter
 */
public class ComboControl extends BaseModifiableControl<JComboBox, String> {
  private static final Pair<String, Icon> EMPTY = new ComboBoxItem(" ", null);
  private final Factory<List<Pair<String, Icon>>> myDataFactory;
  private boolean myNullable;
  private final Map<String, Icon> myIcons = new HashMap<>();
  private final ItemListener myCommitListener = new ItemListener() {
    @Override
    public void itemStateChanged(ItemEvent e) {
      setModified();
      commit();
    }
  };

  public ComboControl(final GenericDomValue genericDomValue, final Factory<List<Pair<String, Icon>>> dataFactory) {
    this(new DomStringWrapper(genericDomValue), dataFactory);
  }

  public ComboControl(final DomWrapper<String> domWrapper, final Factory<List<Pair<String, Icon>>> dataFactory) {
    super(domWrapper);
    myDataFactory = dataFactory;
    reset();
  }

  public ComboControl(final DomWrapper<String> domWrapper, final Class<? extends Enum> aClass) {
    super(domWrapper);
    myDataFactory = createEnumFactory(aClass);
    reset();
  }

  public final boolean isNullable() {
    return myNullable;
  }

  public final void setNullable(final boolean nullable) {
    myNullable = nullable;
  }

  public ComboControl(final GenericDomValue<?> reference) {
    this(reference, createResolvingFunction(reference));
  }

  public static Factory<List<Pair<String, Icon>>> createResolvingFunction(final GenericDomValue<?> reference) {
    return () -> {
      final Converter converter = reference.getConverter();
      if (converter instanceof ResolvingConverter) {
        final AbstractConvertContext context = new AbstractConvertContext() {
          @Override
          @NotNull
          public DomElement getInvocationElement() {
            return reference;
          }
        };
        final ResolvingConverter resolvingConverter = (ResolvingConverter)converter;
        final Collection<Object> variants = resolvingConverter.getVariants(context);
        final List<Pair<String, Icon>> all =
          new ArrayList<>(ContainerUtil.map(variants, s -> Pair
            .create(ElementPresentationManager.getElementName(s), ElementPresentationManager.getIcon(s))));
        all.addAll(ContainerUtil.map(resolvingConverter.getAdditionalVariants(context), new Function() {
          @Override
          public Object fun(Object s) {
            return new Pair(s, null);
          }
        }));
        return all;
      }
      return Collections.emptyList();
    };
  }

  public static Factory<List<Pair<String, Icon>>> createPresentationFunction(final Factory<Collection<?>> variantFactory) {
    return () -> ContainerUtil.map(variantFactory.create(), new Function<Object, Pair<String, Icon>>() {
      @Override
      public Pair<String, Icon> fun(final Object s) {
        return Pair.create(ElementPresentationManager.getElementName(s), ElementPresentationManager.getIcon(s));
      }
    });
  }

  static Factory<List<Pair<String, Icon>>> createEnumFactory(final Class<? extends Enum> aClass) {
    return () -> ContainerUtil.map2List(aClass.getEnumConstants(), new Function<Enum, Pair<String, Icon>>() {
      @Override
      public Pair<String, Icon> fun(final Enum s) {
        return Pair.create(NamedEnumUtil.getEnumValueByElement(s), ElementPresentationManager.getIcon(s));
      }
    });
  }

  public static <T extends Enum> JComboBox createEnumComboBox(final Class<T> type) {
    return tuneUpComboBox(new JComboBox(), createEnumFactory(type));
  }

  private static JComboBox tuneUpComboBox(final JComboBox comboBox, Factory<List<Pair<String, Icon>>> dataFactory) {
    final List<Pair<String, Icon>> list = dataFactory.create();
    final Set<String> standardValues = new HashSet<>();
    for (final Pair<String, Icon> pair : list) {
      comboBox.addItem(new ComboBoxItem(pair));
      standardValues.add(pair.first);
    }
    return initComboBox(comboBox, object -> standardValues.contains(object));
  }

  private static class ComboBoxItem extends Pair<String,Icon> {

    public ComboBoxItem(String first, Icon second) {
      super(first, second);
    }

    public ComboBoxItem(Pair<String,Icon> pair) {
      super(pair.first, pair.second);
    }

    public String toString() {
      return StringUtil.notNullize(first);
    }
  }

  static JComboBox initComboBox(final JComboBox comboBox, final Condition<String> validity) {
    comboBox.setEditable(false);
    comboBox.setPrototypeDisplayValue(new ComboBoxItem("A", null));
    comboBox.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final Pair<String, Icon> pair = (Pair<String, Icon>)value;
        final String text = pair == null ? null : pair.first;
        setText(text);
        final Dimension dimension = getPreferredSize();
        if (!validity.value(text)) {
          setFont(getFont().deriveFont(Font.ITALIC));
          setForeground(JBColor.RED);
        }
        setIcon(pair == null ? null : pair.second);
        setPreferredSize(new Dimension(-1, dimension.height));
        return this;
      }
    });
    return comboBox;
  }

  @Override
  protected JComboBox createMainComponent(final JComboBox boundedComponent) {
    return initComboBox(boundedComponent == null ? new JComboBox() : boundedComponent, object -> isValidValue(object));
  }

  public boolean isValidValue(final String object) {
    return myNullable && object == EMPTY.first || myIcons.containsKey(object);
  }

  private boolean dataChanged(List<Pair<String, Icon>> newData) {
    final JComboBox comboBox = getComponent();
    final int size = comboBox.getItemCount();
    final List<Pair<String, Icon>> oldData = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      oldData.add((Pair<String, Icon>)comboBox.getItemAt(i));
    }

    if (myNullable) {
      final LinkedList<Pair<String, Icon>> list = new LinkedList<>(newData);
      list.addFirst(EMPTY);
      newData = list;
    }

    return !newData.equals(oldData);
  }

  @Override
  protected boolean isCommitted() {
    return getComponent().isPopupVisible() || super.isCommitted();
  }

  @Override
  protected void doReset() {
    final List<Pair<String, Icon>> data = myDataFactory.create();
    final JComboBox comboBox = getComponent();
    comboBox.removeItemListener(myCommitListener);
    try {
      if (!dataChanged(data)) {
        super.doReset();
        return;
      }

      final String oldValue = getValue();
      myIcons.clear();
      comboBox.removeAllItems();
      if (myNullable) {
        comboBox.addItem(EMPTY);
      }
      for (final Pair<String, Icon> s : data) {
        comboBox.addItem(new ComboBoxItem(s));
        myIcons.put(s.first, s.second);
      }
      setValue(oldValue);
      super.doReset();
    }
    finally {
      comboBox.addItemListener(myCommitListener);
    }
  }

  @Override
  @Nullable
  protected final String getValue() {
    final Pair<String, Icon> pair = (Pair<String, Icon>)getComponent().getSelectedItem();
    return pair == null || pair == EMPTY ? null : pair.first;
  }

  @Override
  protected final void setValue(final String value) {
    final JComboBox component = getComponent();
    if (!isValidValue(value)) {
      component.setEditable(true);
    }
    component.setSelectedItem(new ComboBoxItem(value, myIcons.get(value)));
    component.setEditable(false);
  }


  @Override
  protected void updateComponent() {
    final DomElement domElement = getDomElement();
    if (domElement == null || !domElement.isValid()) return;

    final JComboBox comboBox = getComponent();

    final Project project = getProject();
    ApplicationManager.getApplication().invokeLater(() -> {
      if (!project.isOpen()) return;
      if (!getDomWrapper().isValid()) return;

      final DomElement domElement1 = getDomElement();
      if (domElement1 == null || !domElement1.isValid()) return;

      final DomElementAnnotationsManager manager = DomElementAnnotationsManager.getInstance(project);
      final DomElementsProblemsHolder holder = manager.getCachedProblemHolder(domElement1);
      final List<DomElementProblemDescriptor> errorProblems = holder.getProblems(domElement1);
      final List<DomElementProblemDescriptor> warningProblems = holder.getProblems(domElement1, true, HighlightSeverity.WARNING);

      Color background = getDefaultBackground();
      comboBox.setToolTipText(null);

      if (errorProblems.size() > 0) {
        background = getErrorBackground();
        comboBox.setToolTipText(TooltipUtils.getTooltipText(errorProblems));
      }
      else if (warningProblems.size() > 0) {
        background = getWarningBackground();
        comboBox.setToolTipText(TooltipUtils.getTooltipText(warningProblems));
      }

          final Pair<String, Icon> pair = (Pair<String, Icon>)comboBox.getSelectedItem();
          final String s = pair == null ? null : pair.first;
          background = s != null && s.trim().length() > 0 ? getDefaultBackground() : background;

      comboBox.setBackground(background);
      comboBox.getEditor().getEditorComponent().setBackground(background);
    });

  }
}
