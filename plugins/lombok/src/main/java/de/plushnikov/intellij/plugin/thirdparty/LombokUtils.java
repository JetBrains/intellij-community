package de.plushnikov.intellij.plugin.thirdparty;

import com.intellij.openapi.util.text.StringUtil;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;

import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * @author ProjectLombok Team
 * @author Plushnikov Michail
 */
public class LombokUtils {
  public static final String LOMBOK_INTERN_FIELD_MARKER = "$";

  /* NB: 'notnull' is not part of the pattern because there are lots of @NotNull annotations out there that are crappily named and actually mean
something else, such as 'this field must not be null _when saved to the db_ but its perfectly okay to start out as such, and a no-args
constructor and the implied starts-out-as-null state that goes with it is in fact mandatory' which happens with javax.validation.constraints.NotNull.
Various problems with spring have also been reported. See issue #287, issue #271, and issue #43. */

  public static final Pattern NON_NULL_PATTERN = Pattern.compile("^(?:nonnull)$", Pattern.CASE_INSENSITIVE);
  public static final Pattern NULLABLE_PATTERN = Pattern.compile("^(?:nullable|checkfornull)$", Pattern.CASE_INSENSITIVE);

  public static final Pattern DEPRECATED_PATTERN = Pattern.compile("^(?:deprecated)$", Pattern.CASE_INSENSITIVE);

  /**
   * Generates a getter name from a given field name.
   * <p/>
   * Strategy:
   * <ul>
   * <li>Reduce the field's name to its base name by stripping off any prefix (from {@code Accessors}). If the field name does not fit
   * the prefix list, this method immediately returns {@code null}.</li>
   * <li>If {@code Accessors} has {@code fluent=true}, then return the basename.</li>
   * <li>Pick a prefix. 'get' normally, but 'is' if {@code isBoolean} is true.</li>
   * <li>Only if {@code isBoolean} is true: Check if the field starts with {@code is} followed by a non-lowercase character. If so, return the field name verbatim.</li>
   * <li>Check if the first character of the field is lowercase. If so, check if the second character
   * exists and is title or upper case. If so, uppercase the first character. If not, titlecase the first character.</li>
   * <li>Return the prefix plus the possibly title/uppercased first character, and the rest of the field name.</li>
   * </ul>
   *
   * @param accessors Accessors configuration.
   * @param fieldName the name of the field.
   * @param isBoolean if the field is of type 'boolean'. For fields of type {@code java.lang.Boolean}, you should provide {@code false}.
   * @return The getter name for this field, or {@code null} if this field does not fit expected patterns and therefore cannot be turned into a getter name.
   */
  public static String toGetterName(AccessorsInfo accessors, String fieldName, boolean isBoolean) {
    return toAccessorName(accessors, fieldName, isBoolean, "is", "get");
  }

  /**
   * Generates a setter name from a given field name.
   * <p/>
   * Strategy:
   * <ul>
   * <li>Reduce the field's name to its base name by stripping off any prefix (from {@code Accessors}). If the field name does not fit
   * the prefix list, this method immediately returns {@code null}.</li>
   * <li>If {@code Accessors} has {@code fluent=true}, then return the basename.</li>
   * <li>Only if {@code isBoolean} is true: Check if the field starts with {@code is} followed by a non-lowercase character.
   * If so, replace {@code is} with {@code set} and return that.</li>
   * <li>Check if the first character of the field is lowercase. If so, check if the second character
   * exists and is title or upper case. If so, uppercase the first character. If not, titlecase the first character.</li>
   * <li>Return {@code "set"} plus the possibly title/uppercased first character, and the rest of the field name.</li>
   * </ul>
   *
   * @param accessors Accessors configuration.
   * @param fieldName the name of the field.
   * @param isBoolean if the field is of type 'boolean'. For fields of type {@code java.lang.Boolean}, you should provide {@code false}.
   * @return The setter name for this field, or {@code null} if this field does not fit expected patterns and therefore cannot be turned into a getter name.
   */
  public static String toSetterName(AccessorsInfo accessors, String fieldName, boolean isBoolean) {
    return toAccessorName(accessors, fieldName, isBoolean, "set", "set");
  }

  /**
   * Generates a wither name from a given field name.
   * <p/>
   * Strategy:
   * <ul>
   * <li>Reduce the field's name to its base name by stripping off any prefix (from {@code Accessors}). If the field name does not fit
   * the prefix list, this method immediately returns {@code null}.</li>
   * <li>Only if {@code isBoolean} is true: Check if the field starts with {@code is} followed by a non-lowercase character.
   * If so, replace {@code is} with {@code with} and return that.</li>
   * <li>Check if the first character of the field is lowercase. If so, check if the second character
   * exists and is title or upper case. If so, uppercase the first character. If not, titlecase the first character.</li>
   * <li>Return {@code "with"} plus the possibly title/uppercased first character, and the rest of the field name.</li>
   * </ul>
   *
   * @param accessors Accessors configuration.
   * @param fieldName the name of the field.
   * @param isBoolean if the field is of type 'boolean'. For fields of type {@code java.lang.Boolean}, you should provide {@code false}.
   * @return The wither name for this field, or {@code null} if this field does not fit expected patterns and therefore cannot be turned into a getter name.
   */
  public static String toWitherName(AccessorsInfo accessors, String fieldName, boolean isBoolean) {
    if (accessors.isFluent()) {
      throw new IllegalArgumentException("@Wither does not support @Accessors(fluent=true)");
    }
    return toAccessorName(accessors, fieldName, isBoolean, "with", "with");
  }

  private static String toAccessorName(AccessorsInfo accessorsInfo, String fieldName, boolean isBoolean, String booleanPrefix, String normalPrefix) {
    final String result;

    fieldName = accessorsInfo.removePrefix(fieldName);
    if (accessorsInfo.isFluent()) {
      return fieldName;
    }

    final boolean useBooleanPrefix = isBoolean && !accessorsInfo.isDoNotUseIsPrefix();

    if (useBooleanPrefix) {
      if (fieldName.startsWith("is") && fieldName.length() > 2 && !Character.isLowerCase(fieldName.charAt(2))) {
        final String baseName = fieldName.substring(2);
        result = buildName(booleanPrefix, baseName);
      } else {
        result = buildName(booleanPrefix, fieldName);
      }
    } else {
      result = buildName(normalPrefix, fieldName);
    }
    return result;
  }


  /**
   * Returns all names of methods that would represent the getter for a field with the provided name.
   * <p/>
   * For example if {@code isBoolean} is true, then a field named {@code isRunning} would produce:<br />
   * {@code [isRunning, getRunning, isIsRunning, getIsRunning]}
   *
   * @param accessorsInfo
   * @param fieldName     the name of the field.
   * @param isBoolean     if the field is of type 'boolean'. For fields of type 'java.lang.Boolean', you should provide {@code false}.
   */
  public static Collection<String> toAllGetterNames(AccessorsInfo accessorsInfo, String fieldName, boolean isBoolean) {
    return toAllAccessorNames(accessorsInfo, fieldName, isBoolean, "is", "get");
  }

  /**
   * Returns all names of methods that would represent the setter for a field with the provided name.
   * <p/>
   * For example if {@code isBoolean} is true, then a field named {@code isRunning} would produce:<br />
   * {@code [setRunning, setIsRunning]}
   *
   * @param accessorsInfo
   * @param fieldName     the name of the field.
   * @param isBoolean     if the field is of type 'boolean'. For fields of type 'java.lang.Boolean', you should provide {@code false}.
   */
  public static Collection<String> toAllSetterNames(AccessorsInfo accessorsInfo, String fieldName, boolean isBoolean) {
    return toAllAccessorNames(accessorsInfo, fieldName, isBoolean, "set", "set");
  }

  /**
   * Returns all names of methods that would represent the wither for a field with the provided name.
   * <p/>
   * For example if {@code isBoolean} is true, then a field named {@code isRunning} would produce:<br />
   * {@code [withRunning, withIsRunning]}
   *
   * @param accessorsInfo
   * @param fieldName     the name of the field.
   * @param isBoolean     if the field is of type 'boolean'. For fields of type 'java.lang.Boolean', you should provide {@code false}.
   */
  public static Collection<String> toAllWitherNames(AccessorsInfo accessorsInfo, String fieldName, boolean isBoolean) {
    if (accessorsInfo.isFluent()) {
      throw new IllegalArgumentException("@Wither does not support @Accessors(fluent=true)");
    }
    return toAllAccessorNames(accessorsInfo, fieldName, isBoolean, "with", "with");
  }

  private static Collection<String> toAllAccessorNames(AccessorsInfo accessorsInfo, String fieldName, boolean isBoolean, String booleanPrefix, String normalPrefix) {
    Collection<String> result = new HashSet<String>();

    fieldName = accessorsInfo.removePrefix(fieldName);
    if (accessorsInfo.isFluent()) {
      result.add(StringUtil.decapitalize(fieldName));
      return result;
    }

    if (isBoolean) {
      result.add(buildName(normalPrefix, fieldName));
      result.add(buildName(booleanPrefix, fieldName));

      if (fieldName.startsWith("is") && fieldName.length() > 2 && !Character.isLowerCase(fieldName.charAt(2))) {
        final String baseName = fieldName.substring(2);
        result.add(buildName(normalPrefix, baseName));
        result.add(buildName(booleanPrefix, baseName));
      }
    } else {
      result.add(buildName(normalPrefix, fieldName));
    }
    return result;
  }

  private static String buildName(String prefix, String suffix) {
    return prefix + StringUtil.capitalize(suffix);
  }
}
