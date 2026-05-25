import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @FileName    : SensorHistory.java
 * @Description : 센서 측정값 이력을 관리하고, 24시간 동안 값이 안정적으로
 *                건조 상태를 유지하는지 판단하는 클래스
 */
public class SensorHistory {

    private static final int STABLE_HOURS   = 24;   // 안정 판단 기간 (시간)
    private static final int VALUE_TOLERANCE = 10;   // 안정 판단 허용 오차 (수분 % 기준)

    private final List<SensorReading> readings = new ArrayList<>();

    /**
     * 새로운 센서 측정값을 이력에 추가한다.
     *
     * @param value 센서 ADC 측정값 (0~1023)
     */
    public void addReading(int value) {
        readings.add(new SensorReading(value));
        cleanOldReadings();
    }

    /**
     * 최근 24시간 동안 측정값이 건조 임계값 이상으로 안정적으로 유지되었는지 판단한다.
     * 조건 1: 최근 STABLE_HOURS 이내의 데이터가 존재할 것
     * 조건 2: 측정값 변동 폭이 VALUE_TOLERANCE 이내일 것 (안정)
     * 조건 3: 모든 측정값이 dryThreshold 이하일 것 (건조) - 반전 후: 낮은 값 = 건조
     *
     * @param dryThreshold 건조 판단 기준 ADC 값 (PlantSpecies에서 제공)
     * @return 관수가 필요한 상태이면 true
     */
    public boolean isStableAndDry(int dryThreshold) {
        List<SensorReading> recent = getRecentReadings();
        if (recent.isEmpty()) return false;

        int min = recent.get(0).getValue();
        int max = recent.get(0).getValue();
        for (SensorReading r : recent) {
            if (r.getValue() < min) min = r.getValue();
            if (r.getValue() > max) max = r.getValue();
        }

        boolean isStable = (max - min) <= VALUE_TOLERANCE;
        boolean isDry    = max <= dryThreshold; // 반전 후: 낮은 값 = 건조

        return isStable && isDry;
    }

    /**
     * 물을 준 후 이력을 초기화한다.
     * 알림이 재발송되지 않도록 물 주기 직후에 호출한다.
     */
    public void reset() {
        readings.clear();
    }

    /**
     * 가장 최근에 측정된 센서값을 반환한다.
     *
     * @return 최신 ADC 값, 이력이 없으면 -1
     */
    public int getLatestValue() {
        if (readings.isEmpty()) return -1;
        return readings.get(readings.size() - 1).getValue();
    }

    // ── private ──────────────────────────────────────────────

    /** STABLE_HOURS 이내의 측정값 목록을 반환한다. */
    private List<SensorReading> getRecentReadings() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(STABLE_HOURS);
        List<SensorReading> recent = new ArrayList<>();
        for (SensorReading r : readings) {
            if (r.getTimestamp().isAfter(cutoff)) recent.add(r);
        }
        return recent;
    }

    /** STABLE_HOURS 를 초과한 오래된 기록을 제거해 메모리를 절약한다. */
    private void cleanOldReadings() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(STABLE_HOURS);
        readings.removeIf(r -> r.getTimestamp().isBefore(cutoff));
    }
}
