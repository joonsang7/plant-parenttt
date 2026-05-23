import java.time.LocalDateTime;

/**
 * @FileName    : SensorReading.java
 * @Description : 센서에서 읽은 단일 측정값과 측정 시각을 저장하는 데이터 클래스
 *                SensorHistory가 이 객체의 리스트를 보관한다.
 */
public class SensorReading {

    private final int value;
    private final LocalDateTime timestamp;

    /**
     * 측정값을 저장하고 현재 시각을 자동으로 기록한다.
     *
     * @param value 센서 ADC 측정값 (0~1023)
     */
    public SensorReading(int value) {
        this.value     = value;
        this.timestamp = LocalDateTime.now();
    }

    /** @return ADC 측정값 */
    public int getValue() { return value; }

    /** @return 측정 시각 */
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "SensorReading{value=" + value + ", time=" + timestamp + "}";
    }
}
