class MyCallable[**P, R]:
   def __call__(self, *args: P.args, **kwargs: P.kwargs):
       
ex<the_ref>pr: MyCallable[[int, str], bool]
