class CleanupName {
	void test() {
		Object o = "Hello World!";
		try {
			System.out.println(o);
		} finally {
			if (java.util.Collections.singletonList(o).get(0) != null) {
				o.toString();
			}
		}
	}
	void test2() {
		Object o = "Hello World too!";
		try {
			System.out.println(o);
		} finally {
			if (java.util.Collections.singletonList(o).get(0) != null) {
				o.toString();
			}
		}
	}
}
