package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.CodeInsightColors;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

public interface HighlightInfoType {
  HighlightInfoType WRONG_REF = new HighlightInfoTypeImpl(HighlightInfo.ERROR, CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);
  HighlightInfoType ERROR = new HighlightInfoTypeImpl(HighlightInfo.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);

  HighlightInfoType ASPECT_ERROR = new HighlightInfoTypeImpl(HighlightInfo.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);
  HighlightInfoType ASPECT_WARNING = new HighlightInfoTypeImpl(HighlightInfo.WARNING, CodeInsightColors.WARNINGS_ATTRIBUTES);

  HighlightInfoType EJB_ERROR = new HighlightInfoTypeSeverityByKeyAttrBySeverity(HighlightDisplayKey.EJB_ERROR);
  HighlightInfoType EJB_WARNING = new HighlightInfoTypeSeverityByKeyAttrBySeverity(HighlightDisplayKey.EJB_WARNING);

  HighlightInfoType ILLEGAL_DEPENDENCY = new HighlightInfoTypeSeverityByKeyAttrBySeverity(HighlightDisplayKey.ILLEGAL_DEPENDENCY);

  HighlightInfoType WRONG_ELEMENT_NAME = new HighlightInfoTypeImpl(HighlightInfo.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);

  HighlightInfoType UNUSED_SYMBOL = new HighlightInfoTypeSeverityByKey(HighlightDisplayKey.UNUSED_SYMBOL,
                                                                       CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
  HighlightInfoType UNUSED_THROWS_DECL = new HighlightInfoTypeSeverityByKey(HighlightDisplayKey.UNUSED_THROWS_DECL,
                                                                            CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
  HighlightInfoType UNUSED_IMPORT = new HighlightInfoTypeSeverityByKey(HighlightDisplayKey.UNUSED_IMPORT,
                                                                       CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
  HighlightInfoType DEPRECATED = new HighlightInfoTypeSeverityByKey(HighlightDisplayKey.DEPRECATED_SYMBOL,
                                                                    CodeInsightColors.DEPRECATED_ATTRIBUTES);
  HighlightInfoType WRONG_PACKAGE_STATEMENT = new HighlightInfoTypeSeverityByKeyAttrBySeverity(HighlightDisplayKey.WRONG_PACKAGE_STATEMENT);
  HighlightInfoType SILLY_ASSIGNMENT = new HighlightInfoTypeSeverityByKey(HighlightDisplayKey.SILLY_ASSIGNMENT,
                                                                          CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
  HighlightInfoType ACCESS_STATIC_VIA_INSTANCE = new HighlightInfoTypeSeverityByKeyAttrBySeverity(
    HighlightDisplayKey.ACCESS_STATIC_VIA_INSTANCE);

  HighlightInfoType JAVADOC_WRONG_REF = new HighlightInfoTypeSeverityByKey(HighlightDisplayKey.JAVADOC_ERROR,
                                                                           CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);
  HighlightInfoType JAVADOC_ERROR = new HighlightInfoTypeSeverityByKeyAttrBySeverity(HighlightDisplayKey.JAVADOC_ERROR);
  HighlightInfoType UNKNOWN_JAVADOC_TAG = new HighlightInfoTypeSeverityByKeyAttrBySeverity(HighlightDisplayKey.UNKNOWN_JAVADOC_TAG);


  HighlightInfoType LOCAL_VAR = new HighlightInfoTypeImpl(HighlightInfo.INFORMATION, CodeInsightColors.LOCAL_VARIABLE_ATTRIBUTES);
  HighlightInfoType INSTANCE_FIELD = new HighlightInfoTypeImpl(HighlightInfo.INFORMATION, CodeInsightColors.INSTANCE_FIELD_ATTRIBUTES);
  HighlightInfoType STATIC_FIELD = new HighlightInfoTypeImpl(HighlightInfo.INFORMATION, CodeInsightColors.STATIC_FIELD_ATTRIBUTES);
  HighlightInfoType PARAMETER = new HighlightInfoTypeImpl(HighlightInfo.INFORMATION, CodeInsightColors.PARAMETER_ATTRIBUTES);
  // t.o.d.o attributes depend on the t.o.d.o text
  HighlightInfoType TODO = new HighlightInfoTypeImpl(HighlightInfo.INFORMATION, null);
  HighlightInfoType JOIN_POINT = new HighlightInfoTypeImpl(HighlightInfo.INFORMATION, CodeInsightColors.JOIN_POINT);
  HighlightInfoType METHOD_CALL = new HighlightInfoTypeImpl(HighlightInfo.INFORMATION, CodeInsightColors.METHOD_CALL_ATTRIBUTES);
  HighlightInfoType METHOD_DECLARATION = new HighlightInfoTypeImpl(HighlightInfo.INFORMATION,
                                                                   CodeInsightColors.METHOD_DECLARATION_ATTRIBUTES);
  HighlightInfoType CONSTRUCTOR_CALL = new HighlightInfoTypeImpl(HighlightInfo.INFORMATION, CodeInsightColors.CONSTRUCTOR_CALL_ATTRIBUTES);
  HighlightInfoType CONSTRUCTOR_DECLARATION = new HighlightInfoTypeImpl(HighlightInfo.INFORMATION,
                                                                        CodeInsightColors.CONSTRUCTOR_DECLARATION_ATTRIBUTES);
  HighlightInfoType STATIC_METHOD = new HighlightInfoTypeImpl(HighlightInfo.INFORMATION, CodeInsightColors.STATIC_METHOD_ATTRIBUTES);
  HighlightInfoType CLASS_NAME = new HighlightInfoTypeImpl(HighlightInfo.INFORMATION, CodeInsightColors.CLASS_NAME_ATTRIBUTES);
  HighlightInfoType INTERFACE_NAME = new HighlightInfoTypeImpl(HighlightInfo.INFORMATION, CodeInsightColors.INTERFACE_NAME_ATTRIBUTES);
  HighlightInfoType JAVA_KEYWORD = new HighlightInfoTypeImpl(HighlightInfo.INFORMATION, HighlighterColors.JAVA_KEYWORD);
  HighlightInfoType ANNOTATION_NAME = new HighlightInfoTypeImpl(HighlightInfo.INFORMATION, CodeInsightColors.ANNOTATION_NAME_ATTRIBUTES);
  HighlightInfoType ANNOTATION_ATTRIBUTE_NAME = new HighlightInfoTypeImpl(HighlightInfo.INFORMATION,
                                                                          CodeInsightColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES);

  HighlightInfoType SUSPICIOUS_METHOD_NAME = new HighlightInfoTypeImpl(HighlightInfo.WARNING, CodeInsightColors.WARNINGS_ATTRIBUTES);

  HighlightInfoType WARNING = new HighlightInfoTypeImpl(HighlightInfo.WARNING, CodeInsightColors.WARNINGS_ATTRIBUTES);
  HighlightInfoType OVERFLOW_WARNING = new HighlightInfoTypeImpl(HighlightInfo.WARNING, CodeInsightColors.ERRORS_ATTRIBUTES);
  HighlightInfoType UNRESOLVED_SOFT_REFERENCE = new HighlightInfoTypeImpl(HighlightInfo.WARNING, CodeInsightColors.WARNINGS_ATTRIBUTES);

  HighlightInfoType RETURN_OUTSIDE_METHOD = new HighlightInfoTypeImpl(HighlightInfo.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);
  HighlightInfoType UNHANDLED_EXCEPTION = new HighlightInfoTypeImpl(HighlightInfo.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);

  HighlightInfo.Severity getSeverity();

  TextAttributesKey getAttributesKey();

  class HighlightInfoTypeImpl implements HighlightInfoType {
    private final HighlightInfo.Severity mySeverity;
    private final TextAttributesKey myAttributesKey;

    public HighlightInfoTypeImpl(HighlightInfo.Severity severity, TextAttributesKey attributesKey) {
      mySeverity = severity;
      myAttributesKey = attributesKey;
    }

    public HighlightInfo.Severity getSeverity() {
      return mySeverity;
    }

    public TextAttributesKey getAttributesKey() {
      return myAttributesKey;
    }

    public String toString() {
      return "HighlightInfoTypeImpl[severity=" + mySeverity + ", key=" + myAttributesKey + "]";
    }
  }

  class HighlightInfoTypeSeverityByKey implements HighlightInfoType {
    static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.HighlightInfoType.HighlightInfoTypeSeverityByKey");

    private final TextAttributesKey myAttributesKey;
    private final HighlightDisplayKey mySeverityKey;

    public HighlightInfoTypeSeverityByKey(HighlightDisplayKey severityKey, TextAttributesKey attributesKey) {
      mySeverityKey = severityKey;
      myAttributesKey = attributesKey;
    }

    public HighlightInfo.Severity getSeverity() {
      DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
      HighlightDisplayLevel level = settings.getInspectionProfile().getErrorLevel(mySeverityKey);
      LOG.assertTrue(level != HighlightDisplayLevel.DO_NOT_SHOW);
      return level == HighlightDisplayLevel.ERROR ? HighlightInfo.ERROR : HighlightInfo.WARNING;
    }

    public TextAttributesKey getAttributesKey() {
      return myAttributesKey;
    }

    public String toString() {
      return "HighlightInfoTypeSeverityByKey[severity=" + mySeverityKey + ", key=" + myAttributesKey + "]";
    }
  }

  class HighlightInfoTypeSeverityByKeyAttrBySeverity implements HighlightInfoType {
    static final Logger LOG = Logger.getInstance(
      "#com.intellij.codeInsight.daemon.impl.HighlightInfoType.HighlightInfoTypeSeverityByKeyAttrBySeverity");

    private final HighlightDisplayKey mySeverityKey;

    public HighlightInfoTypeSeverityByKeyAttrBySeverity(HighlightDisplayKey severityKey) {
      mySeverityKey = severityKey;
    }

    public HighlightInfo.Severity getSeverity() {
      DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
      HighlightDisplayLevel level = settings.getInspectionProfile().getErrorLevel(mySeverityKey);
      LOG.assertTrue(level != HighlightDisplayLevel.DO_NOT_SHOW);
      return level == HighlightDisplayLevel.ERROR ? HighlightInfo.ERROR : HighlightInfo.WARNING;
    }

    public TextAttributesKey getAttributesKey() {
      return getSeverity() == HighlightInfo.ERROR ? CodeInsightColors.ERRORS_ATTRIBUTES : CodeInsightColors.WARNINGS_ATTRIBUTES;
    }

    public String toString() {
      return "HighlightInfoTypeSeverityByKeyAttrBySeverity[severity=" + mySeverityKey + "]";

    }
  }
}
