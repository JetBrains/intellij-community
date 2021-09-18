from typing import Any

from jsonschema._reflect import namedAny as namedAny
from jsonschema.validators import validator_for as validator_for

parser: Any

def parse_args(args): ...
def main(args=...) -> None: ...
def run(arguments, stdout=..., stderr=...): ...
