class GetterLazyNative {
	private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Boolean>> booleanField = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Boolean>>();
	private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Byte>> byteField = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Byte>>();
	private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Short>> shortField = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Short>>();
	private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Integer>> intField = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Integer>>();
	private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Long>> longField = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Long>>();
	private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Float>> floatField = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Float>>();
	private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Double>> doubleField = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Double>>();
	private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Character>> charField = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<java.lang.Character>>();
	private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<int[]>> intArrayField = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.atomic.AtomicReference<int[]>>();
	@java.lang.SuppressWarnings("all")
	public boolean isBooleanField() {
		java.util.concurrent.atomic.AtomicReference<java.lang.Boolean> value = this.booleanField.get();
		if (value == null) {
			synchronized (this.booleanField) {
				value = this.booleanField.get();
				if (value == null) {
					final boolean actualValue = true;
					value = new java.util.concurrent.atomic.AtomicReference<java.lang.Boolean>(actualValue);
					this.booleanField.set(value);
				}
			}
		}
		return value.get();
	}
	@java.lang.SuppressWarnings("all")
	public byte getByteField() {
		java.util.concurrent.atomic.AtomicReference<java.lang.Byte> value = this.byteField.get();
		if (value == null) {
			synchronized (this.byteField) {
				value = this.byteField.get();
				if (value == null) {
					final byte actualValue = 1;
					value = new java.util.concurrent.atomic.AtomicReference<java.lang.Byte>(actualValue);
					this.byteField.set(value);
				}
			}
		}
		return value.get();
	}
	@java.lang.SuppressWarnings("all")
	public short getShortField() {
		java.util.concurrent.atomic.AtomicReference<java.lang.Short> value = this.shortField.get();
		if (value == null) {
			synchronized (this.shortField) {
				value = this.shortField.get();
				if (value == null) {
					final short actualValue = 1;
					value = new java.util.concurrent.atomic.AtomicReference<java.lang.Short>(actualValue);
					this.shortField.set(value);
				}
			}
		}
		return value.get();
	}
	@java.lang.SuppressWarnings("all")
	public int getIntField() {
		java.util.concurrent.atomic.AtomicReference<java.lang.Integer> value = this.intField.get();
		if (value == null) {
			synchronized (this.intField) {
				value = this.intField.get();
				if (value == null) {
					final int actualValue = 1;
					value = new java.util.concurrent.atomic.AtomicReference<java.lang.Integer>(actualValue);
					this.intField.set(value);
				}
			}
		}
		return value.get();
	}
	@java.lang.SuppressWarnings("all")
	public long getLongField() {
		java.util.concurrent.atomic.AtomicReference<java.lang.Long> value = this.longField.get();
		if (value == null) {
			synchronized (this.longField) {
				value = this.longField.get();
				if (value == null) {
					final long actualValue = 1;
					value = new java.util.concurrent.atomic.AtomicReference<java.lang.Long>(actualValue);
					this.longField.set(value);
				}
			}
		}
		return value.get();
	}
	@java.lang.SuppressWarnings("all")
	public float getFloatField() {
		java.util.concurrent.atomic.AtomicReference<java.lang.Float> value = this.floatField.get();
		if (value == null) {
			synchronized (this.floatField) {
				value = this.floatField.get();
				if (value == null) {
					final float actualValue = 1.0F;
					value = new java.util.concurrent.atomic.AtomicReference<java.lang.Float>(actualValue);
					this.floatField.set(value);
				}
			}
		}
		return value.get();
	}
	@java.lang.SuppressWarnings("all")
	public double getDoubleField() {
		java.util.concurrent.atomic.AtomicReference<java.lang.Double> value = this.doubleField.get();
		if (value == null) {
			synchronized (this.doubleField) {
				value = this.doubleField.get();
				if (value == null) {
					final double actualValue = 1.0;
					value = new java.util.concurrent.atomic.AtomicReference<java.lang.Double>(actualValue);
					this.doubleField.set(value);
				}
			}
		}
		return value.get();
	}
	@java.lang.SuppressWarnings("all")
	public char getCharField() {
		java.util.concurrent.atomic.AtomicReference<java.lang.Character> value = this.charField.get();
		if (value == null) {
			synchronized (this.charField) {
				value = this.charField.get();
				if (value == null) {
					final char actualValue = '1';
					value = new java.util.concurrent.atomic.AtomicReference<java.lang.Character>(actualValue);
					this.charField.set(value);
				}
			}
		}
		return value.get();
	}
	@java.lang.SuppressWarnings("all")
	public int[] getIntArrayField() {
		java.util.concurrent.atomic.AtomicReference<int[]> value = this.intArrayField.get();
		if (value == null) {
			synchronized (this.intArrayField) {
				value = this.intArrayField.get();
				if (value == null) {
					final int[] actualValue = new int[]{1};
					value = new java.util.concurrent.atomic.AtomicReference<int[]>(actualValue);
					this.intArrayField.set(value);
				}
			}
		}
		return value.get();
	}
}