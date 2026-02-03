def top_level1():
    def nested1():
        pass

    def nested2():
        pass


class MyClass:
    def method1(self):
        def nested_in_method1():
            pass

        def nested_in_method2():
            pass

    def method2(self):
        pass


def top_level2():
    class MyNestedClass:
        def method_of_nested_class1(self):
            pass

        def method_of_nested_class2(self):
            pass
