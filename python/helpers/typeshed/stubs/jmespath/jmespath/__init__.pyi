from typing import Any

from jmespath import parser as parser
from jmespath.visitor import Options as Options

def compile(expression): ...
def search(expression, data, options: Any | None = ...): ...
