import lombok.*;
class TrickyDoNothing {
	@interface Getter {}
	@Getter int x;
}

class TrickyDoNothing2 {
	@Getter int x;
	@interface Getter {}
}

class TrickySuccess {
	@Getter int x;
}

class TrickyDoNothing3 {
	void test() {
		class val {}
		val x = null;
	}
}

class TrickyDoSomething {
	void test() {
		val x = null;
		class val {}
	}
}

class DoubleTrickyDoNothing {
	void test() {
		class val {}
		for (int i = 10; i < 20; i++) {
			val y = null;
		}
	}
}

class DoubleTrickyDoSomething {
	void test() {
		for (int j = 10; j < 20; j++) {
			class val {}
		}
		
		for (int i = 10; i < 20; i++) {
			val y = null;
		}
	}
}
