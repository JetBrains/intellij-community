class AbstractBaseResponseHandler:
    pass


def method(response, code):
    if response:
       return code


def func(abstract_base_response_handler, a):
    a1 = AbstractBaseResponseHandler()
    method(a1.response, a1.code)
