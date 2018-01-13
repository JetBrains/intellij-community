package de.plushnikov;

import com.google.common.collect.FluentIterable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public class Bug288 {

  public interface HasCode {
    String getCode();
  }

  public enum GetCode implements com.google.common.base.Function<HasCode, String>, java.util.function.Function<HasCode, String> {
    FUNC;

    @Override
    public String apply(HasCode e) {
      return e.getCode();
    }
  }

  public Set<String> getRegionCodeList(Set<HasCode> regions) {
    return FluentIterable.from(regions).transform(GetCode.FUNC).toSet();
  }

  public static void main(String[] args) {
    Bug288 bug288 = new Bug288();
    bug288.getRegionCodeList(Collections.emptySet());
  }
}
