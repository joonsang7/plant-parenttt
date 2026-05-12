public class RoutineData {
    private String roomName;
    private String sensorName;
    private int threshold;
    private String deviceName;
    private String action;

    public RoutineData(String roomName, String sensorName, int threshold, String deviceName, String action) {
        this.roomName   = roomName;
        this.sensorName = sensorName;
        this.threshold  = threshold;
        this.deviceName = deviceName;
        this.action     = action;
    }

    // 수분값이 기준값 이하면 루틴 실행 (== 에서 <= 로 수정)
    public boolean matches(SensorData data) {
        return roomName.equals(data.getRoomName())
            && sensorName.equals(data.getSensorName())
            && data.getValue() <= threshold;
    }

    public String getDeviceName() { return deviceName; }
    public String getAction()     { return action; }

    public String toString() {
        return "[room=" + roomName + ", sensor=" + sensorName
             + ", threshold<=" + threshold + ", device=" + deviceName + ", action=" + action + "]";
    }
}
