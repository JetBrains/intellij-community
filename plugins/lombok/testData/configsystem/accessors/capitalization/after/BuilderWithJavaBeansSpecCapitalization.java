class BuilderWithJavaBeansSpecCapitalization {
	java.util.List<String> a;
	java.util.List<String> aField;
	String bField;
        BuilderWithJavaBeansSpecCapitalization(final java.util.List<String> a, final java.util.List<String> aField, final String bField) {
		this.a = a;
		this.aField = aField;
		this.bField = bField;
	}
	public static class BuilderWithJavaBeansSpecCapitalizationBuilder {
		private java.util.ArrayList<String> a;
		private java.util.ArrayList<String> aField;
		private String bField;
		BuilderWithJavaBeansSpecCapitalizationBuilder() {
		}
		public BuilderWithJavaBeansSpecCapitalizationBuilder setZ(final String z) {
			if (this.a == null) this.a = new java.util.ArrayList<String>();
			this.a.add(z);
			return this;
		}
		public BuilderWithJavaBeansSpecCapitalizationBuilder setA(final java.util.Collection<? extends String> a) {
			if (this.a == null) this.a = new java.util.ArrayList<String>();
			this.a.addAll(a);
			return this;
		}
		public BuilderWithJavaBeansSpecCapitalizationBuilder clearA() {
			if (this.a != null) this.a.clear();
			return this;
		}
		public BuilderWithJavaBeansSpecCapitalizationBuilder setyField(final String yField) {
			if (this.aField == null) this.aField = new java.util.ArrayList<String>();
			this.aField.add(yField);
			return this;
		}
		public BuilderWithJavaBeansSpecCapitalizationBuilder setaField(final java.util.Collection<? extends String> aField) {
			if (this.aField == null) this.aField = new java.util.ArrayList<String>();
			this.aField.addAll(aField);
			return this;
		}
		public BuilderWithJavaBeansSpecCapitalizationBuilder clearaField() {
			if (this.aField != null) this.aField.clear();
			return this;
		}
		/**
		 * @return {@code this}.
		 */
		public BuilderWithJavaBeansSpecCapitalizationBuilder setbField(final String bField) {
			this.bField = bField;
			return this;
		}
		public BuilderWithJavaBeansSpecCapitalization build() {
			java.util.List<String> a;
			switch (this.a == null ? 0 : this.a.size()) {
			case 0:
				a = java.util.Collections.emptyList();
				break;
			case 1:
				a = java.util.Collections.singletonList(this.a.get(0));
				break;
			default:
				a = java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(this.a));
			}
			java.util.List<String> aField;
			switch (this.aField == null ? 0 : this.aField.size()) {
			case 0:
				aField = java.util.Collections.emptyList();
				break;
			case 1:
				aField = java.util.Collections.singletonList(this.aField.get(0));
				break;
			default:
				aField = java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(this.aField));
			}
			return new BuilderWithJavaBeansSpecCapitalization(a, aField, this.bField);
		}

		public String toString() {
			return "BuilderWithJavaBeansSpecCapitalization.BuilderWithJavaBeansSpecCapitalizationBuilder(a=" + this.a + ", aField=" + this.aField + ", bField=" + this.bField + ")";
		}
	}
	public static BuilderWithJavaBeansSpecCapitalizationBuilder builder() {
		return new BuilderWithJavaBeansSpecCapitalizationBuilder();
	}
}