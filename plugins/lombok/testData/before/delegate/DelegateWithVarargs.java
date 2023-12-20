import lombok.experimental.Delegate;

class DelegateWithVarargs {
  @Delegate private Bar bar;

  private interface Bar {
    void justOneParameter(int... varargs);

    void multipleParameters(String first, int... varargs);

    void array(int[] array);

    void arrayVarargs(int[]... arrayVarargs);
  }
}