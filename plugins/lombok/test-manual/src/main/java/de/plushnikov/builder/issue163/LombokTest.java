package de.plushnikov.builder.issue163;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(builderClassName = "Builder", builderMethodName = "newBuilder")
public final class LombokTest {

  private final String field;
  private final String otherField;

  /**
   * LombokTest Builder.
   *
   * @param field the field value
   * @return a new builder instance with field set
   */
  public static Builder newBuilder(final String field) {
    return new Builder().field(field);
  }
}