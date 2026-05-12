public class Sensor {
    private String sensorName;

    public Sensor(String name) {
        this.sensorName = name;
    }

    public String getSensorName() {
        return sensorName;
    }

    public int readValue() {
        return (int)(Math.random() * 60);
    }
}
