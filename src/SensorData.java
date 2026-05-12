public class SensorData {
    private String roomName;
    private String sensorName;
    private int value;

    public SensorData(String roomName, String sensorName, int value) {
        this.roomName   = roomName;
        this.sensorName = sensorName;
        this.value      = value;
    }

    public String getRoomName()  { return roomName; }
    public String getSensorName(){ return sensorName; }
    public int getValue()        { return value; }
}
