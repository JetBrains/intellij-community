from pydantic import BaseModel

class Model(
    BaseModel,
    <error descr="Pydantic: 'validate_by_alias' and 'validate_by_name' are both False; no input keys are accepted.">validate_by_alias=False</error>,
    validate_by_name=False,
):
    a: int