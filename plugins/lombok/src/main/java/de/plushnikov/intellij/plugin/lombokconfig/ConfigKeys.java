package de.plushnikov.intellij.plugin.lombokconfig;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

public enum ConfigKeys {

  CONFIG_STOP_BUBBLING("config.stopBubbling", "false"),

  LOG_FIELDNAME("lombok.log.fieldName", "log"),
  LOG_FIELD_IS_STATIC("lombok.log.fieldIsStatic", "true"),

  EQUALSANDHASHCODE_CALL_SUPER("lombok.equalsAndHashCode.callSuper", "warn"),
  EQUALSANDHASHCODE_DO_NOT_USE_GETTERS("lombok.equalsAndHashCode.doNotUseGetters", "false"),
  ANYCONSTRUCTOR_SUPPRESS_CONSTRUCTOR_PROPERTIES("lombok.anyConstructor.suppressConstructorProperties", "false"),

  TOSTRING_DO_NOT_USE_GETTERS("lombok.toString.doNotUseGetters", "false"),
  TOSTRING_INCLUDE_FIELD_NAMES("lombok.toString.includeFieldNames", "true"),

  ACCESSORS_PREFIX("lombok.accessors.prefix", ""),
  ACCESSORS_CHAIN("lombok.accessors.chain", "false"),
  ACCESSORS_FLUENT("lombok.accessors.fluent", "false"),
  GETTER_NO_IS_PREFIX("lombok.getter.noIsPrefix", "false"),

  SINGULAR_USE_GUAVA("lombok.singular.useGuava", "false"),
  SINGULAR_AUTO("lombok.singular.auto", "true"),

  NONNULL_EXCEPTIONTYPE("lombok.nonNull.exceptionType", "java.lang.NullPointerException");

  private final String configKey;
  private final String configDefaultValue;

  ConfigKeys(String configKey, String configDefaultValue) {
    this.configKey = configKey;
    this.configDefaultValue = configDefaultValue;
  }

  public String getConfigKey() {
    return configKey;
  }

  public String getConfigDefaultValue() {
    return configDefaultValue;
  }

  //TODO ADD ALL KEYS
  final Collection<String> flagUsageOptions = new HashSet<String>(Arrays.asList(
      "lombok.accessors.flagUsage", "lombok.allArgsConstructor.flagUsage", "lombok.anyConstructor.flagUsage",
      "lombok.builder.flagUsage", "lombok.cleanup.flagUsage", "lombok.data.flagUsage", "lombok.delegate.flagUsage",
      "lombok.equalsAndHashCode.flagUsage", "lombok.experimental.flagUsage", "lombok.extensionMethod.flagUsage",
      "lombok.fieldDefaults.flagUsage", "lombok.getter.flagUsage", "lombok.getter.lazy.flagUsage",
      "lombok.log.apacheCommons.flagUsage", "lombok.log.flagUsage", "lombok.log.javaUtilLogging.flagUsage",
      "lombok.log.log4j.flagUsage", "lombok.log.log4j2.flagUsage", "lombok.log.slf4j.flagUsage",
      "lombok.log.xslf4j.flagUsage", "lombok.noArgsConstructor.flagUsage", "lombok.nonNull.flagUsage",
      "lombok.requiredArgsConstructor.flagUsage", "lombok.setter.flagUsage", "lombok.sneakyThrows.flagUsage",
      "lombok.synchronized.flagUsage", "lombok.toString.flagUsage", "lombok.val.flagUsage", "lombok.value.flagUsage",
      "lombok.wither.flagUsage"));

}
