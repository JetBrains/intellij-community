package dependency

class A(b: B? = null)

class B(testA: test.A, depA: A)