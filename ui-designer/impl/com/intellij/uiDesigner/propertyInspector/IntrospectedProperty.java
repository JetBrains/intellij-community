package com.intellij.uiDesigner.propertyInspector;

import com.intellij.openapi.util.Comparing;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadGridLayoutManager;
import com.intellij.util.ArrayUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class IntrospectedProperty<V> extends Property<RadComponent, V> {
  protected final static Object[] EMPTY_OBJECT_ARRAY=new Object[]{};

  /**
   * This method is used to set property value to "delegee" JComponent
   */
  @NotNull protected final Method myReadMethod;
  /**
   * This method is used to get property value from "delegee" JComponent
   */
  @NotNull private final Method myWriteMethod;

  private final boolean myStoreAsClient;

  @NonNls private static final String INTRO_PREFIX = "Intro:";

  public IntrospectedProperty(final String name,
                              @NotNull final Method readMethod,
                              @NotNull final Method writeMethod,
                              final boolean storeAsClient) {
    super(null, name);
    myReadMethod = readMethod;
    myWriteMethod = writeMethod;
    myStoreAsClient = storeAsClient;
  }

  /**
   * <b>Do not overide this method without serious reason!</b>
   */
  public V getValue(final RadComponent component){
    //noinspection unchecked
    return (V)invokeGetter(component);
  }

  protected Object invokeGetter(final RadComponent component) {
    if (myStoreAsClient) {
      return component.getClientProperty(INTRO_PREFIX + getName());
    }
    try {
      return myReadMethod.invoke(component.getDelegee(), EMPTY_OBJECT_ARRAY);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * <b>Do not overide this method without serious reason!</b>
   */
  protected void setValueImpl(final RadComponent component,final V value) throws Exception{
    invokeSetter(component, value);
  }

  protected void invokeSetter(final RadComponent component, final Object value) throws IllegalAccessException, InvocationTargetException {
    if (myStoreAsClient) {
      component.putClientProperty(INTRO_PREFIX + getName(), value);
    }
    else {
      myWriteMethod.invoke(component.getDelegee(), value);
    }
  }

  /**
   * Serializes (writes) propertie's value
   *
   * @param value property value which should be serialized.
   * @param writer writer which should be used for serialization. It is assumed that
   * before invocation of this method <code>writer</code> already has opened tag
   * that corresponds to this property. You can just append some attributes
   * here or add some subtags.
   */
  public void write(@NotNull V value, XmlWriter writer) {
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, value.toString());
  }

  @Override public boolean isModified(final RadComponent component) {
    return component.isMarkedAsModified(this);
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    final V defaultValue = getDefaultValue(component.getDelegee());
    invokeSetter(component, defaultValue);
    markTopmostModified(component, false);
  }

  public void importSnapshotValue(final SnapshotContext context, final JComponent component, final RadComponent radComponent) {
    try {
      //noinspection unchecked
      V value = (V) myReadMethod.invoke(component, EMPTY_OBJECT_ARRAY);
      V defaultValue = getDefaultValue(radComponent.getDelegee());
      if (!Comparing.equal(value, defaultValue)) {
        setValue(radComponent, value);
      }
    }
    catch (Exception e) {
      // ignore
    }
  }

  protected V getDefaultValue(final JComponent delegee) throws Exception {
    if (myStoreAsClient) {
      return null;
    }
    final Constructor constructor = delegee.getClass().getConstructor(ArrayUtil.EMPTY_CLASS_ARRAY);
    constructor.setAccessible(true);
    JComponent newComponent = (JComponent)constructor.newInstance(ArrayUtil.EMPTY_OBJECT_ARRAY);
    //noinspection unchecked
    return (V) myReadMethod.invoke(newComponent, EMPTY_OBJECT_ARRAY);
  }

  @Override
  public boolean appliesTo(final RadComponent component) {
    @NonNls String name = getName();
    //noinspection SimplifiableIfStatement
    if (name.equals("preferredSize") || name.equals("minimumSize") || name.equals("maximumSize")) {
      // our own properties must be used instead
      return !(component.getParent().getLayoutManager() instanceof RadGridLayoutManager);
    }

    // check if property is available in the JDK used by the module containing the component
    final PsiManager psiManager = PsiManager.getInstance(component.getProject());
    final GlobalSearchScope scope = component.getModule().getModuleWithDependenciesAndLibrariesScope(true);
    PsiClass componentClass = psiManager.findClass(component.getComponentClassName(), scope);
    if (componentClass == null) return true;
    final PsiMethod[] psiMethods = componentClass.findMethodsByName(myReadMethod.getName(), true);
    for(PsiMethod method: psiMethods) {
      if (!method.getModifierList().hasModifierProperty(PsiModifier.STATIC) &&
          method.getParameterList().getParametersCount() == 0) {
        return true;
      }
    }
    return false;
  }
}
