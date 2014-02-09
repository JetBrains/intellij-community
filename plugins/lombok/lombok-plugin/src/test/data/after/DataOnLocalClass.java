class DataOnLocalClass1 {
	public static void main(String[] args) {
		class Local {
			final int x;
			String name;
			@java.lang.SuppressWarnings("all")
			public Local(final int x) {
				this.x = x;
			}
			@java.lang.SuppressWarnings("all")
			public int getX() {
				return this.x;
			}
			@java.lang.SuppressWarnings("all")
			public String getName() {
				return this.name;
			}
			@java.lang.SuppressWarnings("all")
			public void setName(final String name) {
				this.name = name;
			}
			@java.lang.Override
			@java.lang.SuppressWarnings("all")
			public boolean equals(final java.lang.Object o) {
				if (o == this) return true;
				if (!(o instanceof Local)) return false;
				final Local other = (Local)o;
				if (!other.canEqual((java.lang.Object)this)) return false;
				if (this.getX() != other.getX()) return false;
				final java.lang.Object this$name = this.getName();
				final java.lang.Object other$name = other.getName();
				if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
				return true;
			}
			@java.lang.SuppressWarnings("all")
			public boolean canEqual(final java.lang.Object other) {
				return other instanceof Local;
			}
			@java.lang.Override
			@java.lang.SuppressWarnings("all")
			public int hashCode() {
				final int PRIME = 59;
				int result = 1;
				result = result * PRIME + this.getX();
				final java.lang.Object $name = this.getName();
				result = result * PRIME + ($name == null ? 0 : $name.hashCode());
				return result;
			}
			@java.lang.Override
			@java.lang.SuppressWarnings("all")
			public java.lang.String toString() {
				return "Local(x=" + this.getX() + ", name=" + this.getName() + ")";
			}
		}
	}
}
class DataOnLocalClass2 {
	{
		class Local {
			final int x;
			class InnerLocal {
				@lombok.NonNull
				String name;
				@java.lang.SuppressWarnings("all")
				public InnerLocal(@lombok.NonNull final String name) {
					if (name == null) {
						throw new java.lang.NullPointerException("name");
					}
					this.name = name;
				}
				@lombok.NonNull
				@java.lang.SuppressWarnings("all")
				public String getName() {
					return this.name;
				}
				@java.lang.SuppressWarnings("all")
				public void setName(@lombok.NonNull final String name) {
					if (name == null) {
						throw new java.lang.NullPointerException("name");
					}
					this.name = name;
				}
				@java.lang.Override
				@java.lang.SuppressWarnings("all")
				public boolean equals(final java.lang.Object o) {
					if (o == this) return true;
					if (!(o instanceof Local.InnerLocal)) return false;
					final InnerLocal other = (InnerLocal)o;
					if (!other.canEqual((java.lang.Object)this)) return false;
					final java.lang.Object this$name = this.getName();
					final java.lang.Object other$name = other.getName();
					if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
					return true;
				}
				@java.lang.SuppressWarnings("all")
				public boolean canEqual(final java.lang.Object other) {
					return other instanceof Local.InnerLocal;
				}
				@java.lang.Override
				@java.lang.SuppressWarnings("all")
				public int hashCode() {
					final int PRIME = 59;
					int result = 1;
					final java.lang.Object $name = this.getName();
					result = result * PRIME + ($name == null ? 0 : $name.hashCode());
					return result;
				}
				@java.lang.Override
				@java.lang.SuppressWarnings("all")
				public java.lang.String toString() {
					return "Local.InnerLocal(name=" + this.getName() + ")";
				}
			}
			@java.lang.SuppressWarnings("all")
			public Local(final int x) {
				this.x = x;
			}
			@java.lang.SuppressWarnings("all")
			public int getX() {
				return this.x;
			}
			@java.lang.Override
			@java.lang.SuppressWarnings("all")
			public boolean equals(final java.lang.Object o) {
				if (o == this) return true;
				if (!(o instanceof Local)) return false;
				final Local other = (Local)o;
				if (!other.canEqual((java.lang.Object)this)) return false;
				if (this.getX() != other.getX()) return false;
				return true;
			}
			@java.lang.SuppressWarnings("all")
			public boolean canEqual(final java.lang.Object other) {
				return other instanceof Local;
			}
			@java.lang.Override
			@java.lang.SuppressWarnings("all")
			public int hashCode() {
				final int PRIME = 59;
				int result = 1;
				result = result * PRIME + this.getX();
				return result;
			}
			@java.lang.Override
			@java.lang.SuppressWarnings("all")
			public java.lang.String toString() {
				return "Local(x=" + this.getX() + ")";
			}
		}
	}
}