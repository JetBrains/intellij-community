package de.plushnikov.builder.generic.util.collection;

import java.util.List;

public class SingularList2<T> {
	private List rawTypes;
	private List<Integer> integers;
	private List<T> generics;
	private List<? extends Number> extendsGenerics;

	@SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	SingularList2(final List rawTypes, final List<Integer> integers, final List<T> generics, final List<? extends Number> extendsGenerics) {
		this.rawTypes = rawTypes;
		this.integers = integers;
		this.generics = generics;
		this.extendsGenerics = extendsGenerics;
	}


	@SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static class SingularList2Builder<T> {
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private java.util.ArrayList<Object> rawTypes;
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private java.util.ArrayList<Integer> integers;
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private java.util.ArrayList<T> generics;
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private java.util.ArrayList<Number> extendsGenerics;

		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		SingularList2Builder() {
		}

		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public SingularList2Builder<T> rawType(final Object rawType) {
			if (this.rawTypes == null) this.rawTypes = new java.util.ArrayList<Object>();
			this.rawTypes.add(rawType);
			return this;
		}

		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public SingularList2Builder<T> rawTypes(final java.util.Collection<?> rawTypes) {
			if (this.rawTypes == null) this.rawTypes = new java.util.ArrayList<Object>();
			this.rawTypes.addAll(rawTypes);
			return this;
		}

		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public SingularList2Builder<T> clearRawTypes() {
			if (this.rawTypes != null) this.rawTypes.clear();
			return this;
		}

		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public SingularList2Builder<T> integer(final Integer integer) {
			if (this.integers == null) this.integers = new java.util.ArrayList<Integer>();
			this.integers.add(integer);
			return this;
		}

		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public SingularList2Builder<T> integers(final java.util.Collection<? extends Integer> integers) {
			if (this.integers == null) this.integers = new java.util.ArrayList<Integer>();
			this.integers.addAll(integers);
			return this;
		}

		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public SingularList2Builder<T> clearIntegers() {
			if (this.integers != null) this.integers.clear();
			return this;
		}

		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public SingularList2Builder<T> generic(final T generic) {
			if (this.generics == null) this.generics = new java.util.ArrayList<T>();
			this.generics.add(generic);
			return this;
		}

		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public SingularList2Builder<T> generics(final java.util.Collection<? extends T> generics) {
			if (this.generics == null) this.generics = new java.util.ArrayList<T>();
			this.generics.addAll(generics);
			return this;
		}

		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public SingularList2Builder<T> clearGenerics() {
			if (this.generics != null) this.generics.clear();
			return this;
		}

		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public SingularList2Builder<T> extendsGeneric(final Number extendsGeneric) {
			if (this.extendsGenerics == null) this.extendsGenerics = new java.util.ArrayList<Number>();
			this.extendsGenerics.add(extendsGeneric);
			return this;
		}

		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public SingularList2Builder<T> extendsGenerics(final java.util.Collection<? extends Number> extendsGenerics) {
			if (this.extendsGenerics == null) this.extendsGenerics = new java.util.ArrayList<Number>();
			this.extendsGenerics.addAll(extendsGenerics);
			return this;
		}

		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public SingularList2Builder<T> clearExtendsGenerics() {
			if (this.extendsGenerics != null) this.extendsGenerics.clear();
			return this;
		}

		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public SingularList2<T> build() {
			List<Object> rawTypes;
			switch (this.rawTypes == null ? 0 : this.rawTypes.size()) {
				case 0:
					rawTypes = java.util.Collections.emptyList();
					break;

				case 1:
					rawTypes = java.util.Collections.singletonList(this.rawTypes.get(0));
					break;

				default:
					rawTypes = java.util.Collections.unmodifiableList(new java.util.ArrayList<Object>(this.rawTypes));
			}
			List<Integer> integers;
			switch (this.integers == null ? 0 : this.integers.size()) {
				case 0:
					integers = java.util.Collections.emptyList();
					break;

				case 1:
					integers = java.util.Collections.singletonList(this.integers.get(0));
					break;

				default:
					integers = java.util.Collections.unmodifiableList(new java.util.ArrayList<Integer>(this.integers));
			}
			List<T> generics;
			switch (this.generics == null ? 0 : this.generics.size()) {
				case 0:
					generics = java.util.Collections.emptyList();
					break;

				case 1:
					generics = java.util.Collections.singletonList(this.generics.get(0));
					break;

				default:
					generics = java.util.Collections.unmodifiableList(new java.util.ArrayList<T>(this.generics));
			}
			List<Number> extendsGenerics;
			switch (this.extendsGenerics == null ? 0 : this.extendsGenerics.size()) {
				case 0:
					extendsGenerics = java.util.Collections.emptyList();
					break;

				case 1:
					extendsGenerics = java.util.Collections.singletonList(this.extendsGenerics.get(0));
					break;

				default:
					extendsGenerics = java.util.Collections.unmodifiableList(new java.util.ArrayList<Number>(this.extendsGenerics));
			}
			return new SingularList2<T>(rawTypes, integers, generics, extendsGenerics);
		}

		@Override
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public String toString() {
			return "SingularList2.SingularList2Builder(rawTypes=" + this.rawTypes + ", integers=" + this.integers + ", generics=" + this.generics + ", extendsGenerics=" + this.extendsGenerics + ")";
		}
	}

	@SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static <T> SingularList2Builder<T> builder() {
		return new SingularList2Builder<T>();
	}
}
