public class App {
    public static void main(String[] args) {

        // ── 설정값 ──────────────────────────────────────────
        String BROKER_IP = "192.0.0.2";  // ← 맥북 내부 IP
        // ────────────────────────────────────────────────────

        // 루틴 스토리지 객체 생성 
        RoutineStorage storage = new RoutineStorage();

        // 룸 만들기 
        Room flowerPotRoom = new Room("화분");

        // 센서 만들기 — 자동으로 MQTT 브로커에 연결하고 구독 시작
        Sensor moistureSensor = new MqttSensor("수분센서", BROKER_IP, "home/화분/moisture");

        // 센서를 룸에 설치
        flowerPotRoom.addSensor(moistureSensor);

        Room[] myRooms = { flowerPotRoom };

        // 루틴 만들기 — 수분값이 30 이하면 물 주기 알림
        RoutineData routine1 = new RoutineData("화분", "수분센서", 30, "알림", "물을 주세요!");

        storage.addRoutine(routine1);

        // 허브 가동
        Hub smartHub = new Hub();
        while (true) {
            System.out.println("-------------------");
            smartHub.work(myRooms, storage);
            try {
                Thread.sleep(5000);
            } catch (Exception e) {}
        }
    }
}
