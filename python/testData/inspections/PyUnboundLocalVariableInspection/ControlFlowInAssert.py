# PY-1138
from models import ChartRequest
from components.dbutil import DbSession

def foo(requestId):
    with DbSession() as db:
        req = db.query(ChartRequest).get(requestId)
        assert req is not None, u"Invalid request"
        print req

