package com.wrq.rearranger.util;


import com.wrq.rearranger.settings.RearrangerSettings
import com.wrq.rearranger.settings.atomicAttributes.AndNotAttribute
import com.wrq.rearranger.settings.attributeGroups.Rule
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.junit.Assert

/**
 * Defines common infrastructure for the {@link Rule} builders.
 * 
 * @author Denis Zhdanov
 * @since 5/17/12 10:43 AM
 * @param <T>   rule class
 */
public abstract class AbstractRuleBuilder<T> extends BuilderSupport {

  @NotNull def RearrangerSettings settings
  
  /**
   * Holds rule customization handlers in the form {@code 'property id -> closure'} where <code>'property id'</code>
   * is expected to be one of the {@link RearrangerTestDsl#getValue()}  predefined constants}.
   * <p/>
   * Corresponding value is a closure that receives three arguments - target attribute value, attribute customization map
   * and target rule instance .
   */
  @NotNull private def myHandlers = [:].withDefault {
    { key -> Assert.fail("No handler for the rule attribute '${key}'") }
  }

  @Override
  protected void nodeCompleted(Object parent, Object node) {
    if (!parent && node) {
      registerRule(settings, node as T)
    }
  }

  @Override
  protected void setParent(Object parent, Object child) {
  }

  @Override
  protected Object createNode(Object name) {
    if (name == 'create') {
      return createRule()
    }
    createNode(name, [:], [])
  }

  @Override
  protected Object createNode(Object name, Object value) {
    createNode(name, [:], value)
  }

  @Override
  protected Object createNode(Object name, Map attributes) {
    createNode(name, attributes, [])
  }

  @Override
  protected Object createNode(Object name, Map attributes, Object value) {
    if (value) {
      for (i in [value].flatten()) {
        myHandlers[name](i, attributes, current)
      }
    }
    else {
      myHandlers[name](null, attributes, current)
    }
    
    getCurrent()
  }

  @Nullable
  protected abstract T createRule()

  protected abstract void registerRule(@NotNull RearrangerSettings settings, @NotNull T rule)

  /**
   * Registers given handler for the rule customization within the given data.
   * <p/>
   * Example:
   * <pre>
   *   register('modifier', PsiModifier.FINAL, { value, attributes, rule -&gt;
   *       rule.finalAttribute.value = value
   *       if (attributes.invert) rule.finalAttribute.invert = true
   *   })
   * </pre>
   * 
   * @param specifier  target rule attribute specifier used at the test DSL
   * @param handler    closure to apply target information to the rule instance. Accepts two values - target rule instance
   *                   and additional rule attribute information map
   */
  protected void registerHandler(@NotNull RearrangerTestDsl specifier, @NotNull Closure handler) {
    myHandlers[specifier.value] = handler
    true
  }

  /**
   * Allows to get a closure with the following properties:
   * <pre>
   * <ul>
   *   <li>receives two arguments - target attribute data and target rule;</li>
   *   <li>
   *     assumes that target attribute (identified by the 'propertyName' method argument) IS-A {@link AndNotAttribute}
   *     and updates its value at the given rule object in accordance with the given attribute data;
   *   </li>
   * </ul>
   * </pre>
   * I.e. returned closure looks like <code>{ attributes, rule -> ...}</code>, it configure <code>'rule.propertyName'</code>
   * according to the <code>'attributes'</code> argument.
   * 
   * @param propertyName  name of the target attribute property at the target rule class
   * @return              closure to use for updating target attribute
   */
  @NotNull
  protected static Closure createBooleanAttributeHandler(@NotNull final String propertyName) {
    { attributes, T rule ->
      rule."$propertyName".value = true
      if (attributes[RearrangerTestDsl.INVERT.value]) {
        rule."$propertyName".invert = true
      }
    }
  }

  /**
   * Very similar to the {@link #createBooleanAttributeHandler(java.lang.String)} but targets boolean property with the given name
   * at the given rule argument.
   * 
   * @param propertyName  name of the target attribute property at the target rule class
   * @return              closure to use for updating target attribute
   */
  @NotNull
  protected static Closure createRawBooleanAttributeHandler(@NotNull final String propertyName) {
    { attributes, T rule -> rule."$propertyName" = true }
  }
  
  protected static Closure createStringAttributeHandler(@NotNull final String propertyName) {
    { value, attributes, T rule ->
      rule."$propertyName".match = true
      rule."$propertyName".expression = value
    }
  }
}
