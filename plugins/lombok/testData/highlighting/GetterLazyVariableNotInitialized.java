import lombok.Getter;

public class <warning descr="Class 'GetterLazyVariableNotInitialized' is never used">GetterLazyVariableNotInitialized</warning> {

    private final String hoge;

    public <warning descr="Constructor 'GetterLazyVariableNotInitialized(java.lang.String)' is never used">GetterLazyVariableNotInitialized</warning>(String hoge) {
      this.hoge = hoge;
    }

    // without error
    @Getter(lazy = true)
    private final String method = hoge;

    // with error "Variable .. might not have been initialized"
    @Getter
    private final String methodWithError = <error descr="Variable 'hoge' might not have been initialized">hoge</error>;

  /* Example Lombok will change 'method' to:
    private final AtomicReference<Object> method = new AtomicReference();
    public String getMethod() {
        Object value = this.method.get();
        if (value == null) {
            synchronized(this.method) {
                value = this.method.get();
                if (value == null) {
                    String actualValue = this.hoge;
                    value = actualValue == null ? this.method : actualValue;
                    this.method.set(value);
                }
            }
        }
        return (String)(value == this.method ? null : value);
    }
   */
}
