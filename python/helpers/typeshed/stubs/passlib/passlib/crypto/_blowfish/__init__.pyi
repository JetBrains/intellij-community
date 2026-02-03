from passlib.crypto._blowfish.unrolled import BlowfishEngine as BlowfishEngine

def raw_bcrypt(password, ident, salt, log_rounds): ...

__all__ = ["BlowfishEngine", "raw_bcrypt"]
