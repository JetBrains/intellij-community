class NonNullOnParameter extends Thread {
	NonNullOnParameter(@lombok.NonNull String arg) {
		this(arg, "");
	}
	
	NonNullOnParameter(@lombok.NonNull String arg, @lombok.NonNull String arg2) {
		super(arg);
		if (arg == null) throw new NullPointerException();
	}
	
	public void test2(@lombok.NonNull String arg, @lombok.NonNull String arg2, @lombok.NonNull String arg3) {
		if (arg2 == null) {
			throw new NullPointerException("arg2");
		}
		if (arg == null) System.out.println("Hello");
	}
	
	public void test3(@lombok.NonNull String arg) {
		if (arg != null) throw new IllegalStateException();
	}
	
	public void test(@lombok.NonNull String stringArg, @lombok.NonNull String arg2, @lombok.NonNull int primitiveArg) {
		
	}
	
	public void test(@lombok.NonNull String arg) {
		System.out.println("Hey");
		if (arg == null) throw new NullPointerException();
	}
}