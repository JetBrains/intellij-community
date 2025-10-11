# Simulate an unrelated symbol that has the same attribute name with proper docs
class Response:
    """

    :ivar trigger_id: Identifier for the trigger associated with the response.
    :type trigger_id: int
    """
    trigger_id: int = 1


# In another scope, use an untyped parameter and access the same attribute name
def do(dto=None):
    dto.trig<the_ref>ger_id
