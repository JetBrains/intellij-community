from m import Router

router = Router()
router.route(-2)
router.route(<warning descr="Expected type 'int', got 'str' instead">""</warning>)