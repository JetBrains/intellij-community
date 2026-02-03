class TestClass:
        def __init__(self):
                MyClass.testMethod("Hello World")


class MyClass:
        #@staticmethod
        def te<caret>stMethod(text):
                print (text)
        testMethod = staticmethod(testMethod)


if __name__ == '__main__':
        TestClass()
