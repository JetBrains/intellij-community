# This is <GRAMMAR_ERROR descr="EN_A_VS_AN">a</GRAMMAR_ERROR> error
# (in a multiline comment
#  with  parentheses)
# Я - Пришелец!

def foo():
    """
    'OVERRIDE_JIRA_FIX_VERSIONS_TO_MERGE_REQUESTS'

    Returns:
        x: foo

    """
    pass

def foo2(common_start=None):
    # raise ValueError(f"For the {common_start} attributes, None is only permitted if they are both None.")
    return 1