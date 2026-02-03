import lombok.NonNull;
import lombok.Setter;

import java.util.Date;

public class SetterOnParam {
  @Setter(onParam = @__(@Deprecated))
  private int intField;

  @Setter(onParam_ = {@Deprecated})
  private int intField2;

  @Deprecated
  <error descr="Annotation 'java.lang.Deprecated' already present on field and will be duplicated by onX configuration">@Setter(onMethod_ = @Deprecated)</error>
  private int intFieldDeprecatedAnnotation;

  /**
   * Some javadoc
   * @deprecated some message
   */
  <error descr="Annotation 'java.lang.Deprecated' already present on field and will be duplicated by onX configuration">@Setter(onMethod_ = @Deprecated)</error>
  private int intFieldDeprecatedJavaDoc;

  @NonNull
  <error descr="Annotation 'lombok.NonNull' already present on field and will be duplicated by onX configuration">@Setter(onParam_ = @NonNull)</error>
  private Object someObj;

  @javax.annotation.Nonnull
  <error descr="Annotation 'javax.annotation.Nonnull' already present on field and will be duplicated by onX configuration">@Setter(onParam_ = @javax.annotation.Nonnull)</error>
  private Integer someInteger;

  @org.jetbrains.annotations.NotNull
  <error descr="Annotation 'org.jetbrains.annotations.NotNull' already present on field and will be duplicated by onX configuration">@Setter(onParam_ = @org.jetbrains.annotations.NotNull)</error>
  private Date someDate;
}
