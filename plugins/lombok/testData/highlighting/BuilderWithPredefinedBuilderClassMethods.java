package org.example;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Map;

public class BuilderWithPredefinedBuilderClassMethods {
  public static void main(String[] args) {
    new BuilderWithPredefinedBuilderClassMethods().test();
    System.out.println(new MathContext(1).getSomeInt());
  }

  public static class MathContext {
    private final int someInt;

    public MathContext(int someInt) {
      this.someInt = someInt;
    }

    public int getSomeInt() {
      return someInt;
    }
  }

  public void test() {
    MathContext defaultMathContext = new MathContext(8);
    MathContext customRounding = new MathContext(3);

    final Context.Builder contextBuilder = Context.builder();
    Context context = contextBuilder.withResultRounding(defaultMathContext)
      .withResultRounding("custom1", customRounding) // should not complains about the typing!
      .build();


    System.out.println(context.getResultRoundings());
    System.out.println(context.getResultRoundings().size());
    System.out.println(context.getResultRounding());
    System.out.println(context.getResultRounding().equals(defaultMathContext));
    System.out.println(context.getResultRounding("custom1").equals(customRounding));
  }


  @EqualsAndHashCode
  @ToString
  @Builder(setterPrefix = "with", toBuilder = true, builderClassName = "Builder")
  public static class Context {

    @Singular("resultRounding")
    private final Map<String, MathContext> resultRounding;

    public MathContext getResultRounding() {
      return resultRounding.get("default");
    }

    public MathContext getResultRounding(String key) {
      return resultRounding.get(key);
    }

    public Map<String, MathContext> getResultRoundings() {
      return resultRounding;
    }

    public static class Builder {
      public Builder() {
        resultRounding$key = new ArrayList<>();
        resultRounding$value = new ArrayList<>();
      }

      private void removeRounding(String key) {
        if (resultRounding$key.contains(key)) {
          int index = resultRounding$key.indexOf(key);
          resultRounding$key.remove(key);
          resultRounding$value.remove(index);
        }
      }

      public Builder withResultRounding(MathContext mathContext) {
        removeRounding("default");
        resultRounding$key.add("default");
        resultRounding$value.add(mathContext);
        return this;
      }
    }
  }
}
