public class Test {
  public void foo() {
    Properties properties = new Properties();
    InputStream inStream = null;
    try {
      removeCustomPrefixFromProperties(propertiesFile);

      inStream = new FileInputStream(propertiesFile);
      properties.load(inStream);

      Enumeration<?> propertyNames = properties.propertyNames();
      while (propertyNames.hasMoreElements()) {
        String name = (String) propertyNames.nextElement();

        setValue(name, properties.getProperty(name));
      }

    } catch (FileNotFoundException e) {
      System.err.println(e.getMessage());
      System.exit(-1);
    }
    catch (IOException e) {
      LOG.error(e.getMessage(), e);
    } finally {
      if (inStream != null) {
        try {
          inStream.close();
        } catch (IOException e) {
          LOG.info(e);
        }
      }
    }
  }
}