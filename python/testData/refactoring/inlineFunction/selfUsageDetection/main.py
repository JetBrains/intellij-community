class MyClass:
    def method(self):
        self.another()
        self.another()

    def another(self):
        pass


inst = MyClass()
MyClass.met<caret>hod(inst)
MyClass().method()