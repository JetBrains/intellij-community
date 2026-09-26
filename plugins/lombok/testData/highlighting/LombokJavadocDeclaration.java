// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;

public class <warning descr="Class 'LombokJavadocDeclaration' is never used">LombokJavadocDeclaration</warning> {
  /**
   * Explicit accessor docs.
   *
   * @param explicit explicit value
   * @return explicit value
   */
  @Getter
  @Setter
  public int explicit;

  /**
   * Getter-only docs.
   *
   * @return getterOnly value
   */
  @Getter
  public int getterOnly;

  /**
   * Getter-only docs with unnecessary param tag.
   *
   * <warning descr="Tag 'param' is not allowed here">@param</warning> getterOnlyWithUnnecessaryParam value
   * @return getterOnlyWithUnnecessaryParam value
   */
  @Getter
  public int getterOnlyWithUnnecessaryParam;

  /**
   * Setter-only docs.
   *
   * @param setterOnly new value for setterOnly
   * <warning descr="Tag 'return' is not allowed here">@return</warning> setterOnly value
   */
  @Setter
  public int <warning descr="Public field 'setterOnly' is assigned but never accessed">setterOnly</warning>;

  /**
   * Mismatched setter docs.
   *
   * <warning descr="Tag 'param' is not allowed here">@param</warning> wrongName input param
   * @return field value
   */
  @Getter
  @Setter
  public int wronglyReferencedParam;

  /**
   * Disabled setter docs.
   *
   * <warning descr="Tag 'param' is not allowed here">@param</warning> noSetter value
   */
  @Setter(AccessLevel.NONE)
  public int <warning descr="Public field 'noSetter' is assigned but never accessed">noSetter</warning>;

  /**
   * Duplicate return docs.
   *
   * @return duplicateReturn value
   * <warning descr="Duplicate @return tag"><warning descr="Tag 'return' is not allowed here">@return</warning></warning> duplicateReturn value again
   */
  @Getter
  public int duplicateReturn;

  /**
   * Duplicate setter param docs.
   *
   * @param duplicateParam duplicateParam value
   * <warning descr="Tag 'param' is not allowed here">@param</warning> duplicateParam value again
   */
  @Setter
  public int <warning descr="Public field 'duplicateParam' is assigned but never accessed">duplicateParam</warning>;

  @Getter
  public Runnable nestedDeclaration = new Runnable() {
    /**
     * Nested declaration docs.
     *
     * <warning descr="Tag 'return' is not allowed here">@return</warning> nothing
     */
    @Override
    public void run() {
    }
  };

  @Data
  public static class <warning descr="Class 'DataHolder' is never used">DataHolder</warning> {
    /**
     * Data accessor docs.
     *
     * @return data value
     * @param data data value
     */
    public int data;
  }

  @Value
  public static class <warning descr="Class 'ValueHolder' is never used">ValueHolder</warning> {
    /**
     * Value accessor docs.
     *
     * <warning descr="Tag 'param' is not allowed here">@param</warning> value value
     * @return value
     */
    int value;
  }
}
