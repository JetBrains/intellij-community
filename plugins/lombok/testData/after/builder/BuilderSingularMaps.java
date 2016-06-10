import java.util.Map;
import java.util.SortedMap;
class BuilderSingularMaps<K, V> {
	private Map<K, V> women;
	private SortedMap<K, ? extends Number> men;
	@SuppressWarnings("all")
	private Map rawMap;
	private Map<String, V> stringMap;
	@SuppressWarnings("all")
	BuilderSingularMaps(Map<K, V> women, SortedMap<K, ? extends Number> men, Map rawMap, Map<String, V> stringMap) {
		this.women = women;
		this.men = men;
		this.rawMap = rawMap;
		this.stringMap = stringMap;
	}
	@SuppressWarnings("all")
	public static class BuilderSingularMapsBuilder<K, V> {
		@SuppressWarnings("all")
		private java.util.ArrayList<K> women$key;
		@SuppressWarnings("all")
		private java.util.ArrayList<V> women$value;
		@SuppressWarnings("all")
		private java.util.ArrayList<K> men$key;
		@SuppressWarnings("all")
		private java.util.ArrayList<Number> men$value;
		@SuppressWarnings("all")
		private java.util.ArrayList<Object> rawMap$key;
		@SuppressWarnings("all")
		private java.util.ArrayList<Object> rawMap$value;
		@SuppressWarnings("all")
		private java.util.ArrayList<String> stringMap$key;
		@SuppressWarnings("all")
		private java.util.ArrayList<V> stringMap$value;
		@SuppressWarnings("all")
		BuilderSingularMapsBuilder() {
		}
		@SuppressWarnings("all")
		public BuilderSingularMapsBuilder<K, V> woman(K womanKey, V womanValue) {
			if (this.women$key == null) {
				this.women$key = new java.util.ArrayList<K>();
				this.women$value = new java.util.ArrayList<V>();
			}
			this.women$key.add(womanKey);
			this.women$value.add(womanValue);
			return this;
		}
		@SuppressWarnings("all")
		public BuilderSingularMapsBuilder<K, V> women(java.util.Map<? extends K, ? extends V> women) {
			if (this.women$key == null) {
				this.women$key = new java.util.ArrayList<K>();
				this.women$value = new java.util.ArrayList<V>();
			}
			for (java.util.Map.Entry<? extends K, ? extends V> $lombokEntry : women.entrySet()) {
				this.women$key.add($lombokEntry.getKey());
				this.women$value.add($lombokEntry.getValue());
			}
			return this;
		}
		@SuppressWarnings("all")
		public BuilderSingularMapsBuilder<K, V> clearWomen() {
			if (this.women$key != null) {
				this.women$key.clear();
				this.women$value.clear();
			}
			return this;
		}
		@SuppressWarnings("all")
		public BuilderSingularMapsBuilder<K, V> man(K manKey, Number manValue) {
			if (this.men$key == null) {
				this.men$key = new java.util.ArrayList<K>();
				this.men$value = new java.util.ArrayList<Number>();
			}
			this.men$key.add(manKey);
			this.men$value.add(manValue);
			return this;
		}
		@SuppressWarnings("all")
		public BuilderSingularMapsBuilder<K, V> men(java.util.Map<? extends K, ? extends Number> men) {
			if (this.men$key == null) {
				this.men$key = new java.util.ArrayList<K>();
				this.men$value = new java.util.ArrayList<Number>();
			}
			for (java.util.Map.Entry<? extends K, ? extends Number> $lombokEntry : men.entrySet()) {
				this.men$key.add($lombokEntry.getKey());
				this.men$value.add($lombokEntry.getValue());
			}
			return this;
		}
		@SuppressWarnings("all")
		public BuilderSingularMapsBuilder<K, V> clearMen() {
			if (this.men$key != null) {
				this.men$key.clear();
				this.men$value.clear();
			}
			return this;
		}
		@SuppressWarnings("all")
		public BuilderSingularMapsBuilder<K, V> rawMap(Object rawMapKey, Object rawMapValue) {
			if (this.rawMap$key == null) {
				this.rawMap$key = new java.util.ArrayList<Object>();
				this.rawMap$value = new java.util.ArrayList<Object>();
			}
			this.rawMap$key.add(rawMapKey);
			this.rawMap$value.add(rawMapValue);
			return this;
		}
		@SuppressWarnings("all")
		public BuilderSingularMapsBuilder<K, V> rawMap(java.util.Map<?, ?> rawMap) {
			if (this.rawMap$key == null) {
				this.rawMap$key = new java.util.ArrayList<Object>();
				this.rawMap$value = new java.util.ArrayList<Object>();
			}
			for (java.util.Map.Entry<?, ?> $lombokEntry : rawMap.entrySet()) {
				this.rawMap$key.add($lombokEntry.getKey());
				this.rawMap$value.add($lombokEntry.getValue());
			}
			return this;
		}
		@SuppressWarnings("all")
		public BuilderSingularMapsBuilder<K, V> clearRawMap() {
			if (this.rawMap$key != null) {
				this.rawMap$key.clear();
				this.rawMap$value.clear();
			}
			return this;
		}
		@SuppressWarnings("all")
		public BuilderSingularMapsBuilder<K, V> stringMap(String stringMapKey, V stringMapValue) {
			if (this.stringMap$key == null) {
				this.stringMap$key = new java.util.ArrayList<String>();
				this.stringMap$value = new java.util.ArrayList<V>();
			}
			this.stringMap$key.add(stringMapKey);
			this.stringMap$value.add(stringMapValue);
			return this;
		}
		@SuppressWarnings("all")
		public BuilderSingularMapsBuilder<K, V> stringMap(java.util.Map<? extends String, ? extends V> stringMap) {
			if (this.stringMap$key == null) {
				this.stringMap$key = new java.util.ArrayList<String>();
				this.stringMap$value = new java.util.ArrayList<V>();
			}
			for (java.util.Map.Entry<? extends String, ? extends V> $lombokEntry : stringMap.entrySet()) {
				this.stringMap$key.add($lombokEntry.getKey());
				this.stringMap$value.add($lombokEntry.getValue());
			}
			return this;
		}
		@SuppressWarnings("all")
		public BuilderSingularMapsBuilder<K, V> clearStringMap() {
			if (this.stringMap$key != null) {
				this.stringMap$key.clear();
				this.stringMap$value.clear();
			}
			return this;
		}
		@SuppressWarnings("all")
		public BuilderSingularMaps<K, V> build() {
			java.util.Map<K, V> women;
			switch (this.women$key == null ? 0 : this.women$key.size()) {
			case 0:
				women = java.util.Collections.emptyMap();
				break;
			case 1:
				women = java.util.Collections.singletonMap(this.women$key.get(0), this.women$value.get(0));
				break;
			default:
				women = new java.util.LinkedHashMap<K, V>(this.women$key.size() < 1073741824 ? 1 + this.women$key.size() + (this.women$key.size() - 3) / 3 : Integer.MAX_VALUE);
				for (int $i = 0; $i < this.women$key.size(); $i++) women.put(this.women$key.get($i), this.women$value.get($i));
				women = java.util.Collections.unmodifiableMap(women);
			}
			java.util.SortedMap<K, Number> men = new java.util.TreeMap<K, Number>();
			if (this.men$key != null) for (int $i = 0; $i < (this.men$key == null ? 0 : this.men$key.size()); $i++) men.put(this.men$key.get($i), this.men$value.get($i));
			men = java.util.Collections.unmodifiableSortedMap(men);
			java.util.Map<Object, Object> rawMap;
			switch (this.rawMap$key == null ? 0 : this.rawMap$key.size()) {
			case 0:
				rawMap = java.util.Collections.emptyMap();
				break;
			case 1:
				rawMap = java.util.Collections.singletonMap(this.rawMap$key.get(0), this.rawMap$value.get(0));
				break;
			default:
				rawMap = new java.util.LinkedHashMap<Object, Object>(this.rawMap$key.size() < 1073741824 ? 1 + this.rawMap$key.size() + (this.rawMap$key.size() - 3) / 3 : Integer.MAX_VALUE);
				for (int $i = 0; $i < this.rawMap$key.size(); $i++) rawMap.put(this.rawMap$key.get($i), this.rawMap$value.get($i));
				rawMap = java.util.Collections.unmodifiableMap(rawMap);
			}
			java.util.Map<String, V> stringMap;
			switch (this.stringMap$key == null ? 0 : this.stringMap$key.size()) {
			case 0:
				stringMap = java.util.Collections.emptyMap();
				break;
			case 1:
				stringMap = java.util.Collections.singletonMap(this.stringMap$key.get(0), this.stringMap$value.get(0));
				break;
			default:
				stringMap = new java.util.LinkedHashMap<String, V>(this.stringMap$key.size() < 1073741824 ? 1 + this.stringMap$key.size() + (this.stringMap$key.size() - 3) / 3 : Integer.MAX_VALUE);
				for (int $i = 0; $i < this.stringMap$key.size(); $i++) stringMap.put(this.stringMap$key.get($i), this.stringMap$value.get($i));
				stringMap = java.util.Collections.unmodifiableMap(stringMap);
			}
			return new BuilderSingularMaps<K, V>(women, men, rawMap, stringMap);
		}
		@Override
		@SuppressWarnings("all")
		public String toString() {
			return "BuilderSingularMaps.BuilderSingularMapsBuilder(women$key=" + this.women$key + ", women$value=" + this.women$value + ", men$key=" + this.men$key + ", men$value=" + this.men$value + ", rawMap$key=" + this.rawMap$key + ", rawMap$value=" + this.rawMap$value + ", stringMap$key=" + this.stringMap$key + ", stringMap$value=" + this.stringMap$value + ")";
		}
	}
	@SuppressWarnings("all")
	public static <K, V> BuilderSingularMapsBuilder<K, V> builder() {
		return new BuilderSingularMapsBuilder<K, V>();
	}
}
