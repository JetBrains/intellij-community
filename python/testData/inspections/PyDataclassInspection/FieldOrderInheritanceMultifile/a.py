import dataclasses

from mod import Base


@dataclasses.dataclass
class Sub(Base):
    <error descr="Non-default argument(s) follows default argument(s) defined in 'Base'">field_no_default</error>: int
