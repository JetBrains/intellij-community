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

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.ui.UserActivityWatcher;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableCellEditor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * @author peter
 */
public abstract class DomUIFactory {

  public final static ExtensionPointName<Consumer<DomUIFactory>> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.dom.uiControlsProvider");

  public static final Method GET_VALUE_METHOD = ReflectionUtil.getMethod(GenericDomValue.class, "getValue");
  public static final Method SET_VALUE_METHOD = findMethod(GenericDomValue.class, "setValue");
  public static Method GET_STRING_METHOD = ReflectionUtil.getMethod(GenericDomValue.class, "getStringValue");
  public static Method SET_STRING_METHOD = findMethod(GenericDomValue.class, "setStringValue");

  @NotNull
  public static DomUIControl<GenericDomValue> createControl(GenericDomValue element) {
    return createControl(element, false);
  }

  @NotNull
  public static DomUIControl<GenericDomValue> createControl(GenericDomValue element, boolean commitOnEveryChange) {
    return createGenericValueControl(DomUtil.getGenericValueParameter(element.getDomElementType()), element, commitOnEveryChange);
  }

  public static DomUIControl createSmallDescriptionControl(DomElement parent, final boolean commitOnEveryChange) {
    return createLargeDescriptionControl(parent, commitOnEveryChange);
  }

  public static DomUIControl createLargeDescriptionControl(DomElement parent, final boolean commitOnEveryChange) {
    return getDomUIFactory().createTextControl(
      new DomCollectionWrapper<>(parent, parent.getGenericInfo().getCollectionChildDescription("description")), commitOnEveryChange);
  }

  @NotNull
  private static BaseControl createGenericValueControl(final Type type, final GenericDomValue<?> element, boolean commitOnEveryChange) {
    final DomStringWrapper stringWrapper = new DomStringWrapper(element);
    final Class rawType = ReflectionUtil.getRawType(type);
    if (type instanceof Class && Enum.class.isAssignableFrom(rawType)) {
      return new ComboControl(stringWrapper, rawType);
    }
    if (DomElement.class.isAssignableFrom(rawType)) {
      final ComboControl control = new ComboControl(element);
      final Required annotation = element.getAnnotation(Required.class);
      if (annotation == null || !annotation.value() || !annotation.nonEmpty()) {
        control.setNullable(true);
      }
      return control;
    }

    final DomFixedWrapper wrapper = new DomFixedWrapper(element);
    if (type.equals(boolean.class) || type.equals(Boolean.class)) {
      return new BooleanControl(wrapper);
    }
    if (type.equals(String.class)) {
      return getDomUIFactory().createTextControl(wrapper, commitOnEveryChange);
    }

    final BaseControl customControl = getDomUIFactory().createCustomControl(type, stringWrapper, commitOnEveryChange);
    if (customControl != null) return customControl;

    return getDomUIFactory().createTextControl(stringWrapper, commitOnEveryChange);
  }

  @Nullable
  public static Method findMethod(Class clazz, @NonNls String methodName) {
    for (Method method : ReflectionUtil.getClassPublicMethods(clazz)) {
      if (methodName.equals(method.getName())) {
        return method;
      }
    }
    return null;
  }

  public static TableCellEditor createCellEditor(GenericDomValue genericDomValue) {
    return getDomUIFactory().createCellEditor(genericDomValue, DomUtil.extractParameterClassFromGenericType(genericDomValue.getDomElementType()));
  }

  protected abstract TableCellEditor createCellEditor(DomElement element, Class type);

  public abstract UserActivityWatcher createEditorAwareUserActivityWatcher();

  public abstract void setupErrorOutdatingUserActivityWatcher(CommittablePanel panel, DomElement... elements);

  public abstract BaseControl createTextControl(DomWrapper<String> wrapper, final boolean commitOnEveryChange);

  public abstract void registerCustomControl(@NotNull Class aClass, Function<DomWrapper<String>, BaseControl> creator);

  public abstract void registerCustomCellEditor(@NotNull Class aClass, Function<DomElement, TableCellEditor> creator);

  @Nullable
  public abstract BaseControl createCustomControl(final Type type, DomWrapper<String> wrapper, final boolean commitOnEveryChange);

  public static BaseControl createTextControl(GenericDomValue value, final boolean commitOnEveryChange) {
    return getDomUIFactory().createTextControl(new DomStringWrapper(value), commitOnEveryChange);
  }

  public static BaseControl createTextControl(DomWrapper<String> wrapper) {
    return getDomUIFactory().createTextControl(wrapper, false);
  }

  public static DomUIFactory getDomUIFactory() {
    return ServiceManager.getService(DomUIFactory.class);
  }

  public DomUIControl createCollectionControl(DomElement element, DomCollectionChildDescription description) {
    final ColumnInfo columnInfo = createColumnInfo(description, element);
    final Class aClass = DomUtil.extractParameterClassFromGenericType(description.getType());
    return new DomCollectionControl<>(element, description, aClass == null, columnInfo);
  }

  public ColumnInfo createColumnInfo(final DomCollectionChildDescription description,
                                     final DomElement element) {
    final String presentableName = description.getCommonPresentableName(element);
    final Class aClass = DomUtil.extractParameterClassFromGenericType(description.getType());
    if (aClass != null) {
      if (Boolean.class.equals(aClass) || boolean.class.equals(aClass)) {
        return new BooleanColumnInfo(presentableName);
      }

      return new GenericValueColumnInfo(presentableName, aClass, createCellEditor(element, aClass));
    }

    return new StringColumnInfo(presentableName);
  }

  /**
   * Adds an error-checking square that is usually found in the top-right ange of a text editor
   * to the specified CaptionComponent.
   * @param captionComponent The component to add error panel to
   * @param elements DOM elements that will be error-checked
   * @return captionComponent
   */
  public abstract CaptionComponent addErrorPanel(CaptionComponent captionComponent, DomElement... elements);

  public abstract BackgroundEditorHighlighter createDomHighlighter(Project project, PerspectiveFileEditor editor, DomElement element);

}
