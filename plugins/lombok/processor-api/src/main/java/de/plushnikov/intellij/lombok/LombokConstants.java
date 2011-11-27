package de.plushnikov.intellij.lombok;

import lombok.core.TransformationsUtil;

import java.util.regex.Pattern;

/**
 * @author Plushnikov Michail
 */
public interface LombokConstants {
  public static final String LOMBOK_INTERN_FIELD_MARKER = "$";
  public static final Pattern NON_NULL_PATTERN = TransformationsUtil.NON_NULL_PATTERN;
  public static final Pattern NULLABLE_PATTERN = TransformationsUtil.NULLABLE_PATTERN;
}
