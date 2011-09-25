class GetterLazy {


  static class ValueType {

  }

  private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<ValueType>> fieldName = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<ValueType>>();

  @java.lang.SuppressWarnings("all")
  public ValueType getFieldName() {
    java.util.concurrent.atomic.AtomicReference<ValueType> value = this.fieldName.get();
    if (value == null) {
      synchronized (this.fieldName) {
        value = this.fieldName.get();
        if (value == null) {
          value = new java.util.concurrent.atomic.AtomicReference<ValueType>(new ValueType());
          this.fieldName.set(value);
        }
      }
    }
    return value.get();
  }
}