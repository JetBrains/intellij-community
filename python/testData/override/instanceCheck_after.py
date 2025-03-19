class MyType(type):
    def __instancecheck__(self, __instance):
        <selection>return super().__instancecheck__(__instance)</selection>
