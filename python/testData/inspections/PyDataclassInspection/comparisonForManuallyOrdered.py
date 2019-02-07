from dataclasses import dataclass

@dataclass(order=False)
class Test1:
    def __gt__(self, other):
      pass

@dataclass
class Test2:
    def __gt__(self, other):
      pass

print(Test1() < Test1())
print(Test2() < Test2())

print(Test1() > Test1())
print(Test2() > Test2())

print(Test1 < Test1)
print(Test2 < Test2)

print(Test1 > Test1)
print(Test2 > Test2)