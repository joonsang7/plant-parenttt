import java.util.ArrayList;
import java.util.List;

public class Hub {
    public void work(Room[] myRooms, RoutineStorage storage) {
        List<SensorData> collectedData = new ArrayList<>();

        for (Room room : myRooms) {
            ArrayList<Sensor> sensors = room.getSensorList();
            for (Sensor sensor : sensors) {
                String sensorName = sensor.getSensorName();
                int sensorValue   = sensor.readValue();

            
                SensorData data = new SensorData(room.getRoomName(), sensorName, sensorValue);
                collectedData.add(data);
            }
        }

        List<String> actions = storage.decideAction(collectedData);
        for (String action : actions) {
            System.out.println("  -> 허브가 실행할 액션: " + action);
        }
    }
}
