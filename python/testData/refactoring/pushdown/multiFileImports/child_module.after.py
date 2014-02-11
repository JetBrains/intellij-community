from os import abort
from parent_module import Parent

class Child(Parent):
    def should_be_pushed(self):
        abort()