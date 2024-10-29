import dataclasses

from mod import Base


@dataclasses.dataclass
class <error descr="Non-default argument(s) follows default argument(s) defined in 'Base'">Sub</error>(Base):
    field_no_default: int
