from pydantic import BaseModel, ConfigDict

class Model(BaseModel):
    model_config = ConfigDict(
        validate_by_alias=<error descr="Pydantic: 'validate_by_alias' and 'validate_by_name' are both False; no input keys are accepted.">False</error>,
        validate_by_name=False,
    )

    a: int