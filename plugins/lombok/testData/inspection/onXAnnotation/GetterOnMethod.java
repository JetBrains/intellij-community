import lombok.Getter;
import lombok.NonNull;

import java.util.Date;

public class GetterOnMethod {
  @Getter(onMethod = @__(@Deprecated))
  private int intField;

  @Getter(onMethod_ = {@Deprecated})
  private int intField2;

  @Deprecated
  <error descr="Annotation 'java.lang.Deprecated' already present on field and will be duplicated by onX configuration">@Getter(onMethod_ = @Deprecated)</error>
  private int intFieldDeprecatedAnnotation;

  /**
   * Some javadoc
   * @deprecated some message
   */
  <error descr="Annotation 'java.lang.Deprecated' already present on field and will be duplicated by onX configuration">@Getter(onMethod_ = @Deprecated)</error>
  private int intFieldDeprecatedJavaDoc;

  @NonNull
  <error descr="Annotation 'lombok.NonNull' already present on field and will be duplicated by onX configuration">@Getter(onMethod_ = @NonNull)</error>
  private Object someObj;

  @javax.annotation.Nonnull
  <error descr="Annotation 'javax.annotation.Nonnull' already present on field and will be duplicated by onX configuration">@Getter(onMethod_ = @javax.annotation.Nonnull)</error>
  private Integer someInteger;

  @org.jetbrains.annotations.NotNull
  <error descr="Annotation 'org.jetbrains.annotations.NotNull' already present on field and will be duplicated by onX configuration">@Getter(onMethod_ = @org.jetbrains.annotations.NotNull)</error>
  private Date someDate;
}
