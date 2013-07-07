import lombok.Data;
class DataOnLocalClass1 {
	public static void main(String[] args) {
		@Data class Local {
			final int x;
			String name;
		}
	}
}
class DataOnLocalClass2 {
	{
		@Data class Local {
			final int x;
			@Data class InnerLocal {
				@lombok.NonNull String name;
			}
		}
	}
}