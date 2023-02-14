class Test {
   public Wrapper foo(int p) {
   		Object o = new Object() {
   			private String myIntFull = "";
   			public String getIntFull() {
   				return myIntFull;
   			}
   		};

   		if (p > 0) {
   			return new Wrapper("");
   		} else {
   			return new Wrapper("");
   		}
   	}


}