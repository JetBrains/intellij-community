import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSortedMap;
class BuilderSingularGuavaMaps<K, V> {
	private ImmutableMap<K, V> battleaxes;
	private ImmutableSortedMap<Integer, ? extends V> vertices;
	@SuppressWarnings("all")
	private ImmutableBiMap rawMap;
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	BuilderSingularGuavaMaps(final ImmutableMap<K, V> battleaxes, final ImmutableSortedMap<Integer, ? extends V> vertices, final ImmutableBiMap rawMap) {
		this.battleaxes = battleaxes;
		this.vertices = vertices;
		this.rawMap = rawMap;
	}
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static class BuilderSingularGuavaMapsBuilder<K, V> {
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private com.google.common.collect.ImmutableMap.Builder<K, V> battleaxes;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private com.google.common.collect.ImmutableSortedMap.Builder<Integer, V> vertices;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private com.google.common.collect.ImmutableBiMap.Builder<java.lang.Object, java.lang.Object> rawMap;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		BuilderSingularGuavaMapsBuilder() {
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaMapsBuilder<K, V> battleaxe(final K key, final V value) {
			if (this.battleaxes == null) this.battleaxes = com.google.common.collect.ImmutableMap.builder();
			this.battleaxes.put(key, value);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaMapsBuilder<K, V> battleaxes(final java.util.Map<? extends K, ? extends V> battleaxes) {
			if (this.battleaxes == null) this.battleaxes = com.google.common.collect.ImmutableMap.builder();
			this.battleaxes.putAll(battleaxes);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaMapsBuilder<K, V> clearBattleaxes() {
			this.battleaxes = null;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaMapsBuilder<K, V> vertex(final Integer key, final V value) {
			if (this.vertices == null) this.vertices = com.google.common.collect.ImmutableSortedMap.naturalOrder();
			this.vertices.put(key, value);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaMapsBuilder<K, V> vertices(final java.util.Map<? extends Integer, ? extends V> vertices) {
			if (this.vertices == null) this.vertices = com.google.common.collect.ImmutableSortedMap.naturalOrder();
			this.vertices.putAll(vertices);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaMapsBuilder<K, V> clearVertices() {
			this.vertices = null;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaMapsBuilder<K, V> rawMap(final java.lang.Object key, final java.lang.Object value) {
			if (this.rawMap == null) this.rawMap = com.google.common.collect.ImmutableBiMap.builder();
			this.rawMap.put(key, value);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaMapsBuilder<K, V> rawMap(final java.util.Map<?, ?> rawMap) {
			if (this.rawMap == null) this.rawMap = com.google.common.collect.ImmutableBiMap.builder();
			this.rawMap.putAll(rawMap);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaMapsBuilder<K, V> clearRawMap() {
			this.rawMap = null;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaMaps<K, V> build() {
			com.google.common.collect.ImmutableMap<K, V> battleaxes = this.battleaxes == null ? com.google.common.collect.ImmutableMap.<K, V>of() : this.battleaxes.build();
			com.google.common.collect.ImmutableSortedMap<Integer, V> vertices = this.vertices == null ? com.google.common.collect.ImmutableSortedMap.<Integer, V>of() : this.vertices.build();
			com.google.common.collect.ImmutableBiMap<java.lang.Object, java.lang.Object> rawMap = this.rawMap == null ? com.google.common.collect.ImmutableBiMap.<java.lang.Object, java.lang.Object>of() : this.rawMap.build();
			return new BuilderSingularGuavaMaps<K, V>(battleaxes, vertices, rawMap);
		}
		@java.lang.Override
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public java.lang.String toString() {
			return "BuilderSingularGuavaMaps.BuilderSingularGuavaMapsBuilder(battleaxes=" + this.battleaxes + ", vertices=" + this.vertices + ", rawMap=" + this.rawMap + ")";
		}
	}
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static <K, V> BuilderSingularGuavaMapsBuilder<K, V> builder() {
		return new BuilderSingularGuavaMapsBuilder<K, V>();
	}
}