class A(type):
    def __prep<caret>


class B(type):
    @classmethod
    def __prep<caret>

class C(type):
    @decorator
    def __prep<caret>

class D:
    def __prep<caret>

# so the added imports don't offset the carets
from typing import List
