/**
 * @FileName    : MoistureSensor.java
 * @Description : 토양 수분 센서의 공통 인터페이스
 *                ArduinoMoistureSensor 등 구체 클래스가 이를 구현한다.
 */
public interface MoistureSensor {

    /** @return 센서 이름 */
    String getSensorName();

    /**
     * 토양 수분 ADC 값을 반환한다.
     *
     * @return 0~1023 범위의 ADC 값 (높을수록 건조)
     */
    int readValue();

    /**
     * 센서가 화분 흙에 꽂혀 있는지 판단한다.
     * ADC 값이 AIR_THRESHOLD 미만이면 정상 삽입으로 간주한다.
     *
     * @return 센서가 흙에 삽입되어 있으면 true, 공기 중이면 false
     */
    boolean isInserted();
}
