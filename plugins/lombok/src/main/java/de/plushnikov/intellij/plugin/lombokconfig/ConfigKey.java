package de.plushnikov.intellij.plugin.lombokconfig;

public enum ConfigKey {

  CONFIG_STOP_BUBBLING("config.stopBubbling", "false"),

  COPYABLE_ANNOTATIONS("lombok.copyableAnnotations", ""),

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

  ACCESSORS_PREFIX("lombok.accessors.prefix", ""),
  ACCESSORS_CHAIN("lombok.accessors.chain", "false"),
  ACCESSORS_FLUENT("lombok.accessors.fluent", "false"),
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
  FIELD_NAME_CONSTANTS_TYPENAME("lombok.fieldNameConstants.innerTypeName","Fields"),
  // Used for lombok from v1.18.8
  FIELD_NAME_CONSTANTS_UPPERCASE("lombok.fieldNameConstants.uppercase", "false"),

  NO_ARGS_CONSTRUCTOR_EXTRA_PRIVATE("lombok.noArgsConstructor.extraPrivate", "false"),

  BUILDER_CLASS_NAME("lombok.builder.className", "*Builder");
  /*
    ACCESSORS_FLAG_USAGE("lombok.accessors.flagUsage", ""),
    ALLARGSCONSTRUCTOR_FLAG_USAGE("lombok.allArgsConstructor.flagUsage", ""),
    ANYCONSTRUCTOR_FLAG_USAGE("lombok.anyConstructor.flagUsage", ""),
    BUILDER_FLAG_USAGE("lombok.builder.flagUsage", ""),
    SUPER_BUILDER_FLAG_USAGE("lombok.superBuilder.flagUsage", ""),
    CLEANUP_FLAG_USAGE("lombok.cleanup.flagUsage", ""),
    DATA_FLAG_USAGE("lombok.data.flagUsage", ""),
    DELEGATE_FLAG_USAGE("lombok.delegate.flagUsage", ""),
    EQUALSANDHASHCODE_FLAG_USAGE("lombok.equalsAndHashCode.flagUsage", ""),
    EXPERIMENTAL_FLAG_USAGE("lombok.experimental.flagUsage", ""),
    EXTENSIONMETHOD_FLAG_USAGE("lombok.extensionMethod.flagUsage", ""),
    FIELDDEFAULTS_FLAG_USAGE("lombok.fieldDefaults.flagUsage", ""),
    GETTER_FLAG_USAGE("lombok.getter.flagUsage", ""),
    GETTER_LAZY_FLAG_USAGE("lombok.getter.lazy.flagUsage", ""),
    LOG_APACHECOMMONS_FLAG_USAGE("lombok.log.apacheCommons.flagUsage", ""),
    LOG_CUSTOM_USAGE("lombok.log.custom.flagUsage", ""),
    LOG_FLAG_USAGE("lombok.log.flagUsage", ""),
    LOG_JAVAUTILLOGGING_FLAG_USAGE("lombok.log.javaUtilLogging.flagUsage", ""),
    LOG_LOG4J_FLAG_USAGE("lombok.log.log4j.flagUsage", ""),
    LOG_LOG4J2_FLAG_USAGE("lombok.log.log4j2.flagUsage", ""),
    LOG_SLF4J_FLAG_USAGE("lombok.log.slf4j.flagUsage", ""),
    LOG_XSLF4J_FLAG_USAGE("lombok.log.xslf4j.flagUsage", ""),
    LOG_JBOSSLOG_FLAG_USAGE("lombok.log.jbosslog.flagUsage", ""),
    NOARGSCONSTRUCTOR_FLAG_USAGE("lombok.noArgsConstructor.flagUsage", ""),
    NONNULL_FLAG_USAGE("lombok.nonNull.flagUsage", ""),
    REQUIREDARGSCONSTRUCTOR_FLAG_USAGE("lombok.requiredArgsConstructor.flagUsage", ""),
    SETTER_FLAG_USAGE("lombok.setter.flagUsage", ""),
    SNEAKYTHROWS_FLAG_USAGE("lombok.sneakyThrows.flagUsage", ""),
    SYNCHRONIZED_FLAG_USAGE("lombok.synchronized.flagUsage", ""),
    TOSTRING_FLAG_USAGE("lombok.toString.flagUsage", ""),
    VAL_FLAG_USAGE("lombok.val.flagUsage", ""),
    VAR_FLAG_USAGE("lombok.var.flagUsage", ""),
    VALUE_FLAG_USAGE("lombok.value.flagUsage", ""),
    WITHER_FLAG_USAGE("lombok.wither.flagUsage", ""),
    UTILITYCLASS_FLAG_USAGE("lombok.utilityClass.flagUsage", ""),
    HELPER_FLAG_USAGE("lombok.helper.flagUsage", "");
  */
  private final String configKey;
  private final String configDefaultValue;

  ConfigKey(String configKey, String configDefaultValue) {
    this.configKey = configKey.toLowerCase();
    this.configDefaultValue = configDefaultValue;
  }

  public String getConfigKey() {
    return configKey;
  }

  public String getConfigDefaultValue() {
    return configDefaultValue;
  }

  public static ConfigKey fromConfigStringKey(String configStringKey) {
    for (ConfigKey keys : ConfigKey.values()) {
      if (keys.getConfigKey().equalsIgnoreCase(configStringKey)) {
        return keys;
      }
    }
    return null;
  }
}
