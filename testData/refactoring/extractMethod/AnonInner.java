
import javax.swing.*;
import java.awt.event.ActionEvent;

public class ExtractMethods { }
abstract class MyButton
        extends JButton
        {
    protected MyButton( String text ) {
        super( text );
    }
}
class Foo {
    private JButton createOKButton() {
        return new MyButton( "OK" ) {
            public void actionPerformed( ActionEvent e ) {
            <selection>    setVisible( false ); </selection>
            }
        };
    }
}
