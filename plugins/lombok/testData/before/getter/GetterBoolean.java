class Getter {
	@lombok.Getter boolean foo;
	@lombok.Getter boolean isBar;
	@lombok.Getter boolean hasBaz;
}
class MoreGetter {
	@lombok.Getter boolean foo;
	boolean hasFoo() {
		return true;
	}
}
class YetMoreGetter {
	@lombok.Getter boolean foo;
	boolean getFoo() {
		return true;
	}
}