package de.plushnikov.builder.generic.util.collection;

import lombok.Singular;

import java.util.ArrayList;
import java.util.Collection;

public class SingularCollection<T> {
	@Singular private Collection rawTypes;
	@Singular private Collection<Integer> integers;
	@Singular private Collection<T> generics;
	@Singular private Collection<? extends Number> extendsGenerics;

	@java.beans.ConstructorProperties({"rawTypes", "integers", "generics", "extendsGenerics"})
	SingularCollection(Collection rawTypes, Collection<Integer> integers, Collection<T> generics, Collection<? extends Number> extendsGenerics) {
		this.rawTypes = rawTypes;
		this.integers = integers;
		this.generics = generics;
		this.extendsGenerics = extendsGenerics;
	}

	public static void main(String[] args) {
		//SingularCollection.<Float>builder().generic(20.0f).integer(10).extendsGeneric(2).rawType(2.0);
	}

	public static <T> SingularCollectionBuilder<T> builder() {
		return new SingularCollectionBuilder<T>();
	}

	public static class SingularCollectionBuilder<T> {
		private ArrayList<Object> rawTypes;
		private ArrayList<Integer> integers;
		private ArrayList<T> generics;
		private ArrayList<Number> extendsGenerics;

		SingularCollectionBuilder() {
		}

		public SingularCollection.SingularCollectionBuilder<T> rawType(Object rawType) {
			if (this.rawTypes == null) this.rawTypes = new ArrayList<Object>();
			this.rawTypes.add(rawType);
			return this;
		}

		public SingularCollection.SingularCollectionBuilder<T> rawTypes(Collection<?> rawTypes) {
			if (this.rawTypes == null) this.rawTypes = new ArrayList<Object>();
			this.rawTypes.addAll(rawTypes);
			return this;
		}

		public SingularCollection.SingularCollectionBuilder<T> clearRawTypes() {
			if (this.rawTypes != null)
				this.rawTypes.clear();

			return this;
		}

		public SingularCollection.SingularCollectionBuilder<T> integer(Integer integer) {
			if (this.integers == null) this.integers = new ArrayList<Integer>();
			this.integers.add(integer);
			return this;
		}

		public SingularCollection.SingularCollectionBuilder<T> integers(Collection<? extends Integer> integers) {
			if (this.integers == null) this.integers = new ArrayList<Integer>();
			this.integers.addAll(integers);
			return this;
		}

		public SingularCollection.SingularCollectionBuilder<T> clearIntegers() {
			if (this.integers != null)
				this.integers.clear();

			return this;
		}

		public SingularCollection.SingularCollectionBuilder<T> generic(T generic) {
			if (this.generics == null) this.generics = new ArrayList<T>();
			this.generics.add(generic);
			return this;
		}

		public SingularCollection.SingularCollectionBuilder<T> generics(Collection<? extends T> generics) {
			if (this.generics == null) this.generics = new ArrayList<T>();
			this.generics.addAll(generics);
			return this;
		}

		public SingularCollection.SingularCollectionBuilder<T> clearGenerics() {
			if (this.generics != null)
				this.generics.clear();

			return this;
		}

		public SingularCollection.SingularCollectionBuilder<T> extendsGeneric(Number extendsGeneric) {
			if (this.extendsGenerics == null) this.extendsGenerics = new ArrayList<Number>();
			this.extendsGenerics.add(extendsGeneric);
			return this;
		}

		public SingularCollection.SingularCollectionBuilder<T> extendsGenerics(Collection<? extends Number> extendsGenerics) {
			if (this.extendsGenerics == null) this.extendsGenerics = new ArrayList<Number>();
			this.extendsGenerics.addAll(extendsGenerics);
			return this;
		}

		public SingularCollection.SingularCollectionBuilder<T> clearExtendsGenerics() {
			if (this.extendsGenerics != null)
				this.extendsGenerics.clear();

			return this;
		}

		public SingularCollection<T> build() {
			Collection<Object> rawTypes;
			switch (this.rawTypes == null ? 0 : this.rawTypes.size()) {
				case 0:
					rawTypes = java.util.Collections.emptyList();
					break;
				case 1:
					rawTypes = java.util.Collections.singletonList(this.rawTypes.get(0));
					break;
				default:
					rawTypes = java.util.Collections.unmodifiableList(new ArrayList<Object>(this.rawTypes));
			}
			Collection<Integer> integers;
			switch (this.integers == null ? 0 : this.integers.size()) {
				case 0:
					integers = java.util.Collections.emptyList();
					break;
				case 1:
					integers = java.util.Collections.singletonList(this.integers.get(0));
					break;
				default:
					integers = java.util.Collections.unmodifiableList(new ArrayList<Integer>(this.integers));
			}
			Collection<T> generics;
			switch (this.generics == null ? 0 : this.generics.size()) {
				case 0:
					generics = java.util.Collections.emptyList();
					break;
				case 1:
					generics = java.util.Collections.singletonList(this.generics.get(0));
					break;
				default:
					generics = java.util.Collections.unmodifiableList(new ArrayList<T>(this.generics));
			}
			Collection<Number> extendsGenerics;
			switch (this.extendsGenerics == null ? 0 : this.extendsGenerics.size()) {
				case 0:
					extendsGenerics = java.util.Collections.emptyList();
					break;
				case 1:
					extendsGenerics = java.util.Collections.singletonList(this.extendsGenerics.get(0));
					break;
				default:
					extendsGenerics = java.util.Collections.unmodifiableList(new ArrayList<Number>(this.extendsGenerics));
			}

			return new SingularCollection<T>(rawTypes, integers, generics, extendsGenerics);
		}

		public String toString() {
			return "de.plushnikov.builder.generic.util.collection.SingularCollection.SingularCollectionBuilder(rawTypes=" + this.rawTypes + ", integers=" + this.integers + ", generics=" + this.generics + ", extendsGenerics=" + this.extendsGenerics + ")";
		}
	}
}
