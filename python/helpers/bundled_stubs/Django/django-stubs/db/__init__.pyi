from typing import Any

from .backends.base.base import BaseDatabaseWrapper
from .utils import DEFAULT_DB_ALIAS as DEFAULT_DB_ALIAS  # Not exported in __all__
from .utils import DJANGO_VERSION_PICKLE_KEY as DJANGO_VERSION_PICKLE_KEY
from .utils import ConnectionHandler, ConnectionRouter
from .utils import DatabaseError as DatabaseError
from .utils import DataError as DataError
from .utils import Error as Error
from .utils import IntegrityError as IntegrityError
from .utils import InterfaceError as InterfaceError
from .utils import InternalError as InternalError
from .utils import NotSupportedError as NotSupportedError
from .utils import OperationalError as OperationalError
from .utils import ProgrammingError as ProgrammingError

connections: ConnectionHandler
router: ConnectionRouter
# Actually ConnectionProxy, but quacks exactly like BaseDatabaseWrapper, it's not worth distinguishing the two.
connection: BaseDatabaseWrapper

def close_old_connections(**kwargs: Any) -> None: ...
def reset_queries(**kwargs: Any) -> None: ...
