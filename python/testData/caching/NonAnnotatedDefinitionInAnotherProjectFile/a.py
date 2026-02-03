from lib import not_annotated_func_returning_str


x = not_annotated_func_returning_str()

def expects_int(p: int) -> None:
    pass
    
expects_int(x)  # should have no error, because we infer Any for not_annotated_func_returning_str()
    
  