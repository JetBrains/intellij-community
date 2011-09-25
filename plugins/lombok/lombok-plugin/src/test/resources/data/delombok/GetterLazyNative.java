class GetterLazyNative {

  private final AtomicReference<AtomicReference<Boolean>> booleanField = new AtomicReference<AtomicReference<java.lang.Boolean>>();
  private final AtomicReference<AtomicReference<java.lang.Byte>> byteField = new AtomicReference<AtomicReference<java.lang.Byte>>();
  private final AtomicReference<AtomicReference<java.lang.Short>> shortField = new AtomicReference<AtomicReference<java.lang.Short>>();
  private final AtomicReference<AtomicReference<java.lang.Integer>> intField = new AtomicReference<AtomicReference<java.lang.Integer>>();
  private final AtomicReference<AtomicReference<java.lang.Long>> longField = new AtomicReference<AtomicReference<java.lang.Long>>();
  private final AtomicReference<AtomicReference<java.lang.Float>> floatField = new AtomicReference<AtomicReference<java.lang.Float>>();
  private final AtomicReference<AtomicReference<java.lang.Double>> doubleField = new AtomicReference<AtomicReference<java.lang.Double>>();
  private final AtomicReference<AtomicReference<java.lang.Character>> charField = new AtomicReference<AtomicReference<java.lang.Character>>();
  private final AtomicReference<AtomicReference<int[]>> intArrayField = new AtomicReference<AtomicReference<int[]>>();

  @java.lang.SuppressWarnings("all")
  public boolean getBooleanField() {
    AtomicReference<java.lang.Boolean> value = this.booleanField.get();
    if (value == null) {
      synchronized (this.booleanField) {
        value = this.booleanField.get();
        if (value == null) {
          value = new AtomicReference<java.lang.Boolean>(true);
          this.booleanField.set(value);
        }
      }
    }
    return value.get();
  }

  @java.lang.SuppressWarnings("all")
  public byte getByteField() {
    AtomicReference<java.lang.Byte> value = this.byteField.get();
    if (value == null) {
      synchronized (this.byteField) {
        value = this.byteField.get();
        if (value == null) {
          value = new AtomicReference<java.lang.Byte>(1);
          this.byteField.set(value);
        }
      }
    }
    return value.get();
  }

  @java.lang.SuppressWarnings("all")
  public short getShortField() {
    AtomicReference<java.lang.Short> value = this.shortField.get();
    if (value == null) {
      synchronized (this.shortField) {
        value = this.shortField.get();
        if (value == null) {
          value = new AtomicReference<java.lang.Short>(1);
          this.shortField.set(value);
        }
      }
    }
    return value.get();
  }

  @java.lang.SuppressWarnings("all")
  public int getIntField() {
    AtomicReference<java.lang.Integer> value = this.intField.get();
    if (value == null) {
      synchronized (this.intField) {
        value = this.intField.get();
        if (value == null) {
          value = new AtomicReference<java.lang.Integer>(1);
          this.intField.set(value);
        }
      }
    }
    return value.get();
  }

  @java.lang.SuppressWarnings("all")
  public long getLongField() {
    AtomicReference<java.lang.Long> value = this.longField.get();
    if (value == null) {
      synchronized (this.longField) {
        value = this.longField.get();
        if (value == null) {
          value = new AtomicReference<java.lang.Long>(1);
          this.longField.set(value);
        }
      }
    }
    return value.get();
  }

  @java.lang.SuppressWarnings("all")
  public float getFloatField() {
    AtomicReference<java.lang.Float> value = this.floatField.get();
    if (value == null) {
      synchronized (this.floatField) {
        value = this.floatField.get();
        if (value == null) {
          value = new AtomicReference<java.lang.Float>(1.0F);
          this.floatField.set(value);
        }
      }
    }
    return value.get();
  }

  @java.lang.SuppressWarnings("all")
  public double getDoubleField() {
    AtomicReference<java.lang.Double> value = this.doubleField.get();
    if (value == null) {
      synchronized (this.doubleField) {
        value = this.doubleField.get();
        if (value == null) {
          value = new AtomicReference<java.lang.Double>(1.0);
          this.doubleField.set(value);
        }
      }
    }
    return value.get();
  }

  @java.lang.SuppressWarnings("all")
  public char getCharField() {
    AtomicReference<java.lang.Character> value = this.charField.get();
    if (value == null) {
      synchronized (this.charField) {
        value = this.charField.get();
        if (value == null) {
          value = new AtomicReference<java.lang.Character>('1');
          this.charField.set(value);
        }
      }
    }
    return value.get();
  }

  @java.lang.SuppressWarnings("all")
  public int[] getIntArrayField() {
    AtomicReference<int[]> value = this.intArrayField.get();
    if (value == null) {
      synchronized (this.intArrayField) {
        value = this.intArrayField.get();
        if (value == null) {
          value = new AtomicReference<int[]>(new int[]{1});
          this.intArrayField.set(value);
        }
      }
    }
    return value.get();
  }
}