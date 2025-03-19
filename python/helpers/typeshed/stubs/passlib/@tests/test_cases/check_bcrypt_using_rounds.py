from passlib.hash import bcrypt

bcrypt.using(rounds=13).hash("password")
