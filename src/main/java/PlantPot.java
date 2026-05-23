/**
 * @FileName    : PlantPot.java
 * @Description : 물리적 화분 한 개를 표현하는 클래스
 *                아두이노 핀 번호, 식물 정보, 센서, 센서 이력을 하나로 묶는다.
 *                핀 번호(0~5)가 화분의 고유 식별자 역할을 한다.
 */
public class PlantPot {

    private final int pinNumber;          // 아두이노 아날로그 핀 번호 (0 = A0)
    private final Plant plant;
    private final MoistureSensor sensor;
    private final SensorHistory history;
    private boolean notified = false;     // 관수 알림 발송 여부 (중복 방지)

    /**
     * @param pinNumber 아두이노 아날로그 핀 번호 (0~5)
     * @param plant     이 화분에 심긴 식물 객체
     * @param sensor    이 화분에 꽂힌 센서 객체
     */
    public PlantPot(int pinNumber, Plant plant, MoistureSensor sensor) {
        this.pinNumber = pinNumber;
        this.plant     = plant;
        this.sensor    = sensor;
        this.history   = new SensorHistory();
    }

    /**
     * 센서 삽입 여부를 확인한 뒤 측정값을 이력에 추가한다.
     * 센서가 꽂혀있지 않으면 측정을 건너뛴다.
     */
    public void updateSensorData() {
        if (!sensor.isInserted()) {
            System.out.println("[경고] " + plant.getName()
                + " 센서가 화분에 꽂혀있지 않습니다. (A" + pinNumber + ")");
            return;
        }
        int value = sensor.readValue();
        history.addReading(value);
        System.out.println("[A" + pinNumber + "] " + plant.getName()
            + " 수분 ADC: " + value);
    }

    /**
     * 현재 화분이 관수가 필요한 상태인지 판단한다.
     * SensorHistory의 안정성 + 건조 조건을 위임하여 확인한다.
     *
     * @return 24시간 동안 안정적으로 건조 상태를 유지했으면 true
     */
    public boolean needsWatering() {
        return history.isStableAndDry(plant.getSpecies().getDryThreshold());
    }

    /**
     * 물을 준 뒤 이력과 알림 상태를 초기화한다.
     * GUI에서 "물 줬음" 버튼을 누를 때 호출한다.
     */
    public void resetAfterWatering() {
        history.reset();
        notified = false;
        System.out.println("[" + plant.getName() + "] 물 주기 완료, 이력 초기화.");
    }

    // ── Getters ──────────────────────────────────────────────

    /** @return 아두이노 아날로그 핀 번호 */
    public int getPinNumber() { return pinNumber; }

    /** @return 이 화분의 식물 객체 */
    public Plant getPlant() { return plant; }

    /** @return 가장 최근 센서 ADC 값, 이력 없으면 -1 */
    public int getLatestValue() { return history.getLatestValue(); }

    /** @return 관수 알림이 이미 발송되었으면 true */
    public boolean isNotified() { return notified; }

    /** @param notified 알림 발송 여부 설정 */
    public void setNotified(boolean notified) { this.notified = notified; }
}
