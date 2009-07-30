"""
A hack to make pawt.swing point to the java swing library.
This allows code which imports pawt.swing to work on both JDK1.1 and 1.2
"""
swing = None

try:
        import javax.swing.Icon
        from javax import swing
except (ImportError, AttributeError):
        try:
                import java.awt.swing.Icon
                from java.awt import swing
        except (ImportError, AttributeError):
                try:
                        import com.sun.java.swing.Icon
                        from com.sun.java import swing
                except (ImportError, AttributeError):
                        raise ImportError, 'swing not defined in javax.swing or java.awt.swing or com.sun.java.swing'
import sys
def test(panel, size=None, name='Swing Tester'):
        f = swing.JFrame(name, windowClosing=lambda event: sys.exit(0))
        if hasattr(panel, 'init'):
                panel.init()

        f.contentPane.add(panel)
        f.pack()
        if size is not None:
                from java import awt
                f.setSize(apply(awt.Dimension, size))
        f.setVisible(1)
        return f

if swing is not None:
        import pawt, sys
        pawt.swing = swing
        sys.modules['pawt.swing'] = swing
        swing.__dict__['test'] = test
        
        #These two lines help out jythonc to figure out this very strange module
        swing.__dict__['__file__'] = __file__
        swing.__dict__['__jpythonc_name__'] = 'pawt.swing'
