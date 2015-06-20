import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
class BuilderSingularGuavaMaps<K, V> {
	private ImmutableMap<K, V> battleaxes;
	private ImmutableSortedMap<Integer, ? extends V> vertices;
	@SuppressWarnings("all")
	private ImmutableBiMap rawMap;
	@SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	BuilderSingularGuavaMaps(final ImmutableMap<K, V> battleaxes, final ImmutableSortedMap<Integer, ? extends V> vertices, final ImmutableBiMap rawMap) {
		this.battleaxes = battleaxes;
		this.vertices = vertices;
		this.rawMap = rawMap;
	}
	@SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static class BuilderSingularGuavaMapsBuilder<K, V> {
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private ImmutableMap.Builder<K, V> battleaxes;
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private ImmutableSortedMap.Builder<Integer, V> vertices;
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private ImmutableBiMap.Builder<Object, Object> rawMap;
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		BuilderSingularGuavaMapsBuilder() {
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaMapsBuilder<K, V> battleaxe(final K battleaxe$key, final V battleaxe$value) {
			if (this.battleaxes == null) this.battleaxes = ImmutableMap.builder();
			this.battleaxes.put(battleaxe$key, battleaxe$value);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaMapsBuilder<K, V> battleaxes(final java.util.Map<? extends K, ? extends V> battleaxes) {
			if (this.battleaxes == null) this.battleaxes = ImmutableMap.builder();
			this.battleaxes.putAll(battleaxes);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaMapsBuilder<K, V> vertex(final Integer vertex$key, final V vertex$value) {
			if (this.vertices == null) this.vertices = ImmutableSortedMap.naturalOrder();
			this.vertices.put(vertex$key, vertex$value);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaMapsBuilder<K, V> vertices(final java.util.Map<? extends Integer, ? extends V> vertices) {
			if (this.vertices == null) this.vertices = ImmutableSortedMap.naturalOrder();
			this.vertices.putAll(vertices);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaMapsBuilder<K, V> rawMap(final Object rawMap$key, final Object rawMap$value) {
			if (this.rawMap == null) this.rawMap = ImmutableBiMap.builder();
			this.rawMap.put(rawMap$key, rawMap$value);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaMapsBuilder<K, V> rawMap(final java.util.Map<?, ?> rawMap) {
			if (this.rawMap == null) this.rawMap = ImmutableBiMap.builder();
			this.rawMap.putAll(rawMap);
			return this;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderSingularGuavaMaps<K, V> build() {
			ImmutableMap<K, V> battleaxes = this.battleaxes == null ? ImmutableMap.<K, V>of() : this.battleaxes.build();
			ImmutableSortedMap<Integer, V> vertices = this.vertices == null ? ImmutableSortedMap.<Integer, V>of() : this.vertices.build();
			ImmutableBiMap<Object, Object> rawMap = this.rawMap == null ? ImmutableBiMap.<Object, Object>of() : this.rawMap.build();
			return new BuilderSingularGuavaMaps<K, V>(battleaxes, vertices, rawMap);
		}
		@Override
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public String toString() {
			return "BuilderSingularGuavaMaps.BuilderSingularGuavaMapsBuilder(battleaxes=" + this.battleaxes + ", vertices=" + this.vertices + ", rawMap=" + this.rawMap + ")";
		}
	}
	@SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static <K, V> BuilderSingularGuavaMapsBuilder<K, V> builder() {
		return new BuilderSingularGuavaMapsBuilder<K, V>();
	}
}