package de.plushnikov.intellij.plugin.lombokconfig;

public enum ConfigKey {

  CONFIG_STOP_BUBBLING("config.stopBubbling", "false"),

  COPYABLE_ANNOTATIONS("lombok.copyableAnnotations", "", false),

  LOG_FIELDNAME("lombok.log.fieldName", "log"),
  LOG_FIELD_IS_STATIC("lombok.log.fieldIsStatic", "true"),
  LOG_CUSTOM_DECLARATION("lombok.log.custom.declaration", ""),

  EQUALSANDHASHCODE_CALL_SUPER("lombok.equalsAndHashCode.callSuper", "warn"),
  EQUALSANDHASHCODE_DO_NOT_USE_GETTERS("lombok.equalsAndHashCode.doNotUseGetters", "false"),
  ANYCONSTRUCTOR_SUPPRESS_CONSTRUCTOR_PROPERTIES("lombok.anyConstructor.suppressConstructorProperties", "true"),
  ANYCONSTRUCTOR_ADD_CONSTRUCTOR_PROPERTIES("lombok.anyConstructor.addConstructorProperties", "false"),

  TOSTRING_CALL_SUPER("lombok.toString.callSuper", "skip"),
  TOSTRING_DO_NOT_USE_GETTERS("lombok.toString.doNotUseGetters", "false"),
  TOSTRING_INCLUDE_FIELD_NAMES("lombok.toString.includeFieldNames", "true"),
  TOSTRING_ONLY_EXPLICITLY_INCLUDED("lombok.toString.onlyExplicitlyIncluded", "false"),

  ACCESSORS_PREFIX("lombok.accessors.prefix", "", false),
  ACCESSORS_CHAIN("lombok.accessors.chain", "false"),
  ACCESSORS_FLUENT("lombok.accessors.fluent", "false"),
  ACCESSORS_MAKE_FINAL("lombok.accessors.makeFinal", "false"),
  ACCESSORS_JAVA_BEANS_SPEC_CAPITALIZATION("lombok.accessors.capitalization", "BASIC"),
  GETTER_NO_IS_PREFIX("lombok.getter.noIsPrefix", "false"),

  SINGULAR_USE_GUAVA("lombok.singular.useGuava", "false"),
  SINGULAR_AUTO("lombok.singular.auto", "true"),

  FIELDDEFAULTS_FINAL("lombok.fieldDefaults.defaultFinal", "false"),
  FIELDDEFAULTS_PRIVATE("lombok.fieldDefaults.defaultPrivate", "false"),

  NONNULL_EXCEPTIONTYPE("lombok.nonNull.exceptionType", "java.lang.NullPointerException"),

  ADD_GENERATED_ANNOTATION("lombok.addGeneratedAnnotation", "true"),
  ADD_SUPPRESS_FB_WARNINGS("lombok.extern.findbugs.addSuppressFBWarnings", "false"),

  // Used for lombok v1.16.22 to lombok v1.18.2 only!
  FIELD_NAME_CONSTANTS_PREFIX("lombok.fieldNameConstants.prefix", "FIELD_"),
  // Used for lombok v1.16.22 to lombok v1.18.2 only!
  FIELD_NAME_CONSTANTS_SUFFIX("lombok.fieldNameConstants.suffix", ""),
  // Used for lombok from v1.18.4
  FIELD_NAME_CONSTANTS_TYPENAME("lombok.fieldNameConstants.innerTypeName", "Fields"),
  // Used for lombok from v1.18.8
  FIELD_NAME_CONSTANTS_UPPERCASE("lombok.fieldNameConstants.uppercase", "false"),

  NO_ARGS_CONSTRUCTOR_EXTRA_PRIVATE("lombok.noArgsConstructor.extraPrivate", "false"),

  BUILDER_CLASS_NAME("lombok.builder.className", "*Builder"),
  ADD_NULL_ANNOTATIONS("lombok.addNullAnnotations", ""),
  ADD_LOMBOK_GENERATED_ANNOTATION("lombok.addLombokGeneratedAnnotation", "false"),

  ACCESSORS_FLAG_USAGE("lombok.accessors.flagUsage", "ALLOW"),
  ALL_ARGS_CONSTRUCTOR_FLAG_USAGE("lombok.allArgsConstructor.flagUsage", "ALLOW"),
  ANY_CONSTRUCTOR_FLAG_USAGE("lombok.anyConstructor.flagUsage", "ALLOW"),
  BUILDER_FLAG_USAGE("lombok.builder.flagUsage", "ALLOW"),
  SUPER_BUILDER_FLAG_USAGE("lombok.superBuilder.flagUsage", "ALLOW"),
  CLEANUP_FLAG_USAGE("lombok.cleanup.flagUsage", "ALLOW"),
  DATA_FLAG_USAGE("lombok.data.flagUsage", "ALLOW"),
  DELEGATE_FLAG_USAGE("lombok.delegate.flagUsage", "ALLOW"),
  EQUALS_AND_HASHCODE_FLAG_USAGE("lombok.equalsAndHashCode.flagUsage", "ALLOW"),
  EXPERIMENTAL_FLAG_USAGE("lombok.experimental.flagUsage", "ALLOW"),
  EXTENSION_METHOD_FLAG_USAGE("lombok.extensionMethod.flagUsage", "ALLOW"),
  FIELD_DEFAULTS_FLAG_USAGE("lombok.fieldDefaults.flagUsage", "ALLOW"),
  FIELD_NAME_CONSTANT_FLAG_USAGE("lombok.fieldNameConstants.flagUsage", "ALLOW"),
  GETTER_FLAG_USAGE("lombok.getter.flagUsage", "ALLOW"),
  GETTER_LAZY_FLAG_USAGE("lombok.getter.lazy.flagUsage", "ALLOW"),
  HELPER_FLAG_USAGE("lombok.helper.flagUsage", "ALLOW"),
  JACKSONIZED_FLAG_USAGE("lombok.jacksonized.flagUsage", "ALLOW"),
  LOCKED_FLAG_USAGE("lombok.locked.flagUsage", "ALLOW"),
  LOG_APACHE_COMMONS_FLAG_USAGE("lombok.log.apacheCommons.flagUsage", "ALLOW"),
  LOG_CUSTOM_USAGE("lombok.log.custom.flagUsage", "ALLOW"),
  LOG_FLAG_USAGE("lombok.log.flagUsage", "ALLOW"),
  LOG_FLOGGER_FLAG_USAGE("lombok.log.flogger.flagUsage", "ALLOW"),
  LOG_JAVA_UTIL_LOGGING_FLAG_USAGE("lombok.log.javaUtilLogging.flagUsage", "ALLOW"),
  LOG_JBOSSLOG_FLAG_USAGE("lombok.log.jbosslog.flagUsage", "ALLOW"),
  LOG_LOG4J_FLAG_USAGE("lombok.log.log4j.flagUsage", "ALLOW"),
  LOG_LOG4J2_FLAG_USAGE("lombok.log.log4j2.flagUsage", "ALLOW"),
  LOG_SLF4J_FLAG_USAGE("lombok.log.slf4j.flagUsage", "ALLOW"),
  LOG_XSLF4J_FLAG_USAGE("lombok.log.xslf4j.flagUsage", "ALLOW"),
  NO_ARGS_CONSTRUCTOR_FLAG_USAGE("lombok.noArgsConstructor.flagUsage", "ALLOW"),
  NONNULL_FLAG_USAGE("lombok.nonNull.flagUsage", "ALLOW"),
  ONX_FLAG_USAGE("lombok.onX.flagUsage", "ALLOW"),
  REQUIRED_ARGS_CONSTRUCTOR_FLAG_USAGE("lombok.requiredArgsConstructor.flagUsage", "ALLOW"),
  SETTER_FLAG_USAGE("lombok.setter.flagUsage", "ALLOW"),
  SNEAKY_THROWS_FLAG_USAGE("lombok.sneakyThrows.flagUsage", "ALLOW"),
  STANDARD_EXCEPTION_FLAG_USAGE("lombok.standardException.flagUsage", "ALLOW"),
  SYNCHRONIZED_FLAG_USAGE("lombok.synchronized.flagUsage", "ALLOW"),
  TOSTRING_FLAG_USAGE("lombok.toString.flagUsage", "ALLOW"),
  UTILITY_CLASS_FLAG_USAGE("lombok.utilityClass.flagUsage", "ALLOW"),
  VAL_FLAG_USAGE("lombok.val.flagUsage", "ALLOW"),
  VALUE_FLAG_USAGE("lombok.value.flagUsage", "ALLOW"),
  VAR_FLAG_USAGE("lombok.var.flagUsage", "ALLOW"),
  WITH_FLAG_USAGE("lombok.with.flagUsage", "ALLOW"),
  WITHBY_FLAG_USAGE("lombok.withBy.flagUsage", "ALLOW");

  private final String configKey;
  private final String configDefaultValue;
  private final boolean configScalarValue;

  ConfigKey(String configKey, String configDefaultValue) {
    this(configKey, configDefaultValue, true);
  }

  ConfigKey(String configKey, String configDefaultValue, boolean configScalarValue) {
    this.configKey = configKey;
    this.configDefaultValue = configDefaultValue;
    this.configScalarValue = configScalarValue;
  }

  public String getConfigKey() {
    return configKey;
  }

  public String getConfigDefaultValue() {
    return configDefaultValue;
  }

  boolean isConfigScalarValue() {
    return configScalarValue;
  }

  public static ConfigKey fromConfigStringKey(String configStringKey) {
    for (ConfigKey keys : values()) {
      if (keys.getConfigKey().equalsIgnoreCase(configStringKey)) {
        return keys;
      }
    }
    return null;
  }
}
