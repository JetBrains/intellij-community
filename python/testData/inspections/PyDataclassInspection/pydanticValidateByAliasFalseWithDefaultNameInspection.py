from pydantic import BaseModel

class Model(
    BaseModel,
    <warning descr="Pydantic: 'validate_by_alias' is False and 'validate_by_name' defaults to False; set 'validate_by_name=True' to allow field names.">validate_by_alias=False</warning>,
):
    a: int