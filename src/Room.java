import java.util.ArrayList;

public class Room {
    private String roomName;
    private ArrayList<Sensor> sensorList;

    public Room(String name) {
        this.roomName   = name;
        this.sensorList = new ArrayList<>();
    }

    public String getRoomName() { return roomName; }

    public void addSensor(Sensor s) {
        sensorList.add(s);
        System.out.println("  [" + roomName + "]에 '" + s.getSensorName() + "'(이)가 설치되었습니다.");
    }

    public ArrayList<Sensor> getSensorList() { return sensorList; }
}
