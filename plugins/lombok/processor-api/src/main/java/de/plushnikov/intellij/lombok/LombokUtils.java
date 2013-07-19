package de.plushnikov.intellij.lombok;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
   * <p/>
   * First, pick a prefix. 'get' normally, but 'is' if {@code isBoolean} is true.
   * <p/>
   * Then, check if the first character of the field is lowercase. If so, check if the second character
   * exists and is title or upper case. If so, uppercase the first character. If not, titlecase the first character.
   * <p/>
   * return the prefix plus the possibly title/uppercased first character, and the rest of the field name.
   * <p/>
   * Note that for boolean fields, if the field starts with 'is', and the character after that is
   * <b>not</b> a lowercase character, the field name is returned without changing any character's case and without
   * any prefix.
   *
   * @param fieldName the name of the field.
   * @param isBoolean if the field is of type 'boolean'. For fields of type 'java.lang.Boolean', you should provide {@code false}.
   */
  public static String toGetterName(CharSequence fieldName, boolean isBoolean) {
    final String prefix = isBoolean ? "is" : "get";

    if (fieldName.length() == 0) return prefix;

    if (isBoolean && fieldName.toString().startsWith("is") && fieldName.length() > 2 && !Character.isLowerCase(fieldName.charAt(2))) {
      // The field is for example named 'isRunning'.
      return fieldName.toString();
    }

    return buildName(prefix, fieldName.toString());
  }

  /**
   * Generates a setter name from a given field name.
   * <p/>
   * Strategy:
   * <p/>
   * Check if the first character of the field is lowercase. If so, check if the second character
   * exists and is title or upper case. If so, uppercase the first character. If not, titlecase the first character.
   * <p/>
   * return "set" plus the possibly title/uppercased first character, and the rest of the field name.
   * <p/>
   * Note that if the field is boolean and starts with 'is' followed by a non-lowercase letter, the 'is' is stripped and replaced with 'set'.
   *
   * @param fieldName the name of the field.
   * @param isBoolean if the field is of type 'boolean'. For fields of type 'java.lang.Boolean', you should provide {@code false}.
   */
  public static String toSetterName(CharSequence fieldName, boolean isBoolean) {
    if (fieldName.length() == 0) return "set";

    if (isBoolean && fieldName.toString().startsWith("is") && fieldName.length() > 2 && !Character.isLowerCase(fieldName.charAt(2))) {
      // The field is for example named 'isRunning'.
      return "set" + fieldName.toString().substring(2);
    }

    return buildName("set", fieldName.toString());
  }

  private static String buildName(String prefix, String suffix) {
    if (suffix.length() == 0) return prefix;

    char first = suffix.charAt(0);
    if (Character.isLowerCase(first)) {
      boolean useUpperCase = suffix.length() > 2 &&
          (Character.isTitleCase(suffix.charAt(1)) || Character.isUpperCase(suffix.charAt(1)));
      suffix = String.format("%s%s",
          useUpperCase ? Character.toUpperCase(first) : Character.toTitleCase(first),
          suffix.subSequence(1, suffix.length()));
    }
    return String.format("%s%s", prefix, suffix);
  }

  /**
   * Returns all names of methods that would represent the getter for a field with the provided name.
   * <p/>
   * For example if {@code isBoolean} is true, then a field named {@code isRunning} would produce:<br />
   * {@code [isRunning, getRunning, isIsRunning, getIsRunning]}
   *
   * @param fieldName the name of the field.
   * @param isBoolean if the field is of type 'boolean'. For fields of type 'java.lang.Boolean', you should provide {@code false}.
   */
  public static List<String> toAllGetterNames(CharSequence fieldName, boolean isBoolean) {
    if (!isBoolean) return Collections.singletonList(toGetterName(fieldName, false));

    List<String> baseNames = new ArrayList<String>();
    baseNames.add(fieldName.toString());

    // isPrefix = field is called something like 'isRunning', so 'running' could also be the fieldname.
    if (fieldName.toString().startsWith("is") && fieldName.length() > 2 && !Character.isLowerCase(fieldName.charAt(2))) {
      baseNames.add(fieldName.toString().substring(2));
    }

    Set<String> names = new HashSet<String>();
    for (String baseName : baseNames) {
      if (baseName.length() > 0 && Character.isLowerCase(baseName.charAt(0))) {
        baseName = Character.toTitleCase(baseName.charAt(0)) + baseName.substring(1);
      }

      names.add("is" + baseName);
      names.add("get" + baseName);
    }

    return new ArrayList<String>(names);
  }

  /**
   * Returns all names of methods that would represent the setter for a field with the provided name.
   * <p/>
   * For example if {@code isBoolean} is true, then a field named {@code isRunning} would produce:<br />
   * {@code [setRunning, setIsRunning]}
   *
   * @param fieldName the name of the field.
   * @param isBoolean if the field is of type 'boolean'. For fields of type 'java.lang.Boolean', you should provide {@code false}.
   */
  public static List<String> toAllSetterNames(CharSequence fieldName, boolean isBoolean) {
    if (!isBoolean) return Collections.singletonList(toSetterName(fieldName, false));

    List<String> baseNames = new ArrayList<String>();
    baseNames.add(fieldName.toString());

    // isPrefix = field is called something like 'isRunning', so 'running' could also be the fieldname.
    if (fieldName.toString().startsWith("is") && fieldName.length() > 2 && !Character.isLowerCase(fieldName.charAt(2))) {
      baseNames.add(fieldName.toString().substring(2));
    }

    Set<String> names = new HashSet<String>();
    for (String baseName : baseNames) {
      if (baseName.length() > 0 && Character.isLowerCase(baseName.charAt(0))) {
        baseName = Character.toTitleCase(baseName.charAt(0)) + baseName.substring(1);
      }

      names.add("set" + baseName);
    }

    return new ArrayList<String>(names);
  }
}
