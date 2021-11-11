package de.plushnikov.intellij.plugin;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public interface LombokClassNames {
  @NonNls String ACCESSORS = "lombok.experimental.Accessors";
  @NonNls String ACCESS_LEVEL = "lombok.AccessLevel";
  @NonNls String ALL_ARGS_CONSTRUCTOR = "lombok.AllArgsConstructor";
  @NonNls String BUILDER = "lombok.Builder";
  @NonNls String BUILDER_DEFAULT = "lombok.Builder.Default";
  @NonNls String BUILDER_OBTAIN_VIA = "lombok.Builder.ObtainVia";
  @NonNls String CLEANUP = "lombok.Cleanup";
  @NonNls String COMMONS_LOG = "lombok.extern.apachecommons.CommonsLog";
  @NonNls String CUSTOM_LOG = "lombok.CustomLog";
  @NonNls String DATA = "lombok.Data";
  @NonNls String DELEGATE = "lombok.Delegate";
  @NonNls String EQUALS_AND_HASHCODE = "lombok.EqualsAndHashCode";
  @NonNls String EQUALS_AND_HASHCODE_EXCLUDE = "lombok.EqualsAndHashCode.Exclude";
  @NonNls String EQUALS_AND_HASHCODE_INCLUDE = "lombok.EqualsAndHashCode.Include";
  @NonNls String EXPERIMENTAL_DELEGATE = "lombok.experimental.Delegate";
  @NonNls String EXPERIMENTAL_VAR = "lombok.experimental.var";
  @NonNls String EXTENSION_METHOD = "lombok.experimental.ExtensionMethod";
  @NonNls String FIELD_DEFAULTS = "lombok.experimental.FieldDefaults";
  @NonNls String FIELD_NAME_CONSTANTS = "lombok.experimental.FieldNameConstants";
  @NonNls String FIELD_NAME_CONSTANTS_EXCLUDE = "lombok.experimental.FieldNameConstants.Exclude";
  @NonNls String FIELD_NAME_CONSTANTS_INCLUDE = "lombok.experimental.FieldNameConstants.Include";
  @NonNls String FLOGGER = "lombok.extern.flogger.Flogger";
  @NonNls String GETTER = "lombok.Getter";
  @NonNls String JAVA_LOG = "lombok.extern.java.Log";
  @NonNls String JBOSS_LOG = "lombok.extern.jbosslog.JBossLog";
  @NonNls String LOG_4_J = "lombok.extern.log4j.Log4j";
  @NonNls String LOG_4_J_2 = "lombok.extern.log4j.Log4j2";
  @NonNls String NON_FINAL = "lombok.experimental.NonFinal";
  @NonNls String NON_NULL = "lombok.NonNull";
  @NonNls String NO_ARGS_CONSTRUCTOR = "lombok.NoArgsConstructor";
  @NonNls String PACKAGE_PRIVATE = "lombok.experimental.PackagePrivate";
  @NonNls String REQUIRED_ARGS_CONSTRUCTOR = "lombok.RequiredArgsConstructor";
  @NonNls String SETTER = "lombok.Setter";
  @NonNls String SINGULAR = "lombok.Singular";
  @NonNls String SLF_4_J = "lombok.extern.slf4j.Slf4j";
  @NonNls String SNEAKY_THROWS = "lombok.SneakyThrows";
  @NonNls String SUPER_BUILDER = "lombok.experimental.SuperBuilder";
  @NonNls String STANDARD_EXCEPTION = "lombok.experimental.StandardException";
  @NonNls String SYNCHRONIZED = "lombok.Synchronized";
  @NonNls String TOLERATE = "lombok.experimental.Tolerate";
  @NonNls String TO_STRING = "lombok.ToString";
  @NonNls String TO_STRING_EXCLUDE = "lombok.ToString.Exclude";
  @NonNls String TO_STRING_INCLUDE = "lombok.ToString.Include";
  @NonNls String UTILITY_CLASS = "lombok.experimental.UtilityClass";
  @NonNls String VAL = "lombok.val";
  @NonNls String VALUE = "lombok.Value";
  @NonNls String VAR = "lombok.var";
  @NonNls String WITH = "lombok.With";
  @NonNls String WITHER = "lombok.experimental.Wither";
  @NonNls String XSLF_4_J = "lombok.extern.slf4j.XSlf4j";

  List<String> MAIN_LOMBOK_CLASSES = ContainerUtil.immutableList(ALL_ARGS_CONSTRUCTOR, REQUIRED_ARGS_CONSTRUCTOR, NO_ARGS_CONSTRUCTOR,
                                                                 DATA, GETTER, SETTER, EQUALS_AND_HASHCODE, TO_STRING,
                                                                 LOG_4_J, LOG_4_J_2, SLF_4_J, JAVA_LOG, JBOSS_LOG, FLOGGER, COMMONS_LOG,
                                                                 CUSTOM_LOG,
                                                                 BUILDER, SUPER_BUILDER, FIELD_DEFAULTS, VALUE,
                                                                 UTILITY_CLASS, WITH, WITHER, EXPERIMENTAL_DELEGATE,
                                                                 SNEAKY_THROWS, CLEANUP, SYNCHRONIZED, EXTENSION_METHOD);
}
