import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RoutineStorage {
    private List<RoutineData> routines = new ArrayList<>();

    public RoutineStorage() {}

    public static String getCurrentTime() {
        return LocalDateTime.now().toString();
    }

    public void addRoutine(RoutineData routine) {
        routines.add(routine);
        System.out.println("루틴 저장 완료: " + routine);
    }

    public List<String> decideAction(List<SensorData> sensorDataList) {
        List<String> actions = new ArrayList<>();
        for (SensorData data : sensorDataList) {
            for (RoutineData routine : routines) {
                if (routine.matches(data)) {
                    actions.add(routine.getDeviceName() + ":" + routine.getAction());
                }
            }
        }
        return actions;
    }
}
