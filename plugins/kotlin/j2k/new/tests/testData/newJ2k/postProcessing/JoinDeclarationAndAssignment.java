import java.util.Arrays;

public class TestCodeInConstructor {
    private final int height;
    private final int width;
    private final int size;
    private final int[] data;

    public TestCodeInConstructor(int height, int width, int initialValue) {
        this.height = height;
        this.width = width;
        this.size = height * width;
        this.data = new int[size];
        Arrays.fill(data, initialValue);
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public int getSize() {
        return size;
    }

    public int getData(int i) {
        return data[i];
    }

    public void putData(int i, int value) {
        data[i] = value;
    }
}