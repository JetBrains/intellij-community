import java.lang.SuppressWarnings;

@SuppressWarnings("asdfasd")
class Foo {
  {
    //noinspection asdfasdf
    int <TYPO descr="Typo: In word 'adsaf'">adsaf</TYPO> = 1;
  }
}