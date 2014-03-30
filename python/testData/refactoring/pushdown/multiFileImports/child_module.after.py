from parent_module import Parent
from shared_module import module_function


class Child(Parent):
    def should_be_pushed(self):
        module_function()