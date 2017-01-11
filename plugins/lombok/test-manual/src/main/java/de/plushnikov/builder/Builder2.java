package de.plushnikov.builder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Builder2 {
  private BigDecimal value;
  private String currency;

  @JsonIgnore
  public boolean isZero() {
    return value.equals(BigDecimal.ZERO);
  }

  @JsonIgnore
  public boolean isNotZero() {
    return !isZero();
  }

  public static void main(String[] args) {
    Builder2 builder2 = Builder2.builder().currency("aaa").value(BigDecimal.TEN).build();
    System.out.println(builder2);
  }
}
