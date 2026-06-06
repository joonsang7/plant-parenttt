package com.plantparentt.sensor;

import com.plantparentt.config.AppConfig;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @FileName : SensorHistory.java
 * @Description : 센서 측정값 이력을 관리하고, 24시간 동안 값이 안정적으로
 *              건조 상태를 유지하는지 판단하는 클래스.
 *
 *              스레드 안전성: 모든 public/private 메서드를 synchronized로 선언하여
 *              6시간 스케줄러 스레드와 EDT(수분량 체크 버튼)의 동시 접근으로 인한
 *              ConcurrentModificationException을 방지한다.
 */
public class SensorHistory {

    // 고정 크기의 큐 대신 List를 사용하되, 오래된 기록은 주기적으로 제거하여 메모리를 절약한다.
    // final 로 선언하여 readings 참조 자체는 변경할 수 없도록 한다. (내용은 변경 가능)
    private final List<SensorReading> readings = new ArrayList<>();

    /**
     * 새로운 센서 측정값을 이력에 추가한다.
     *
     * @param value 수분 % (0~100)
     */
    public synchronized void addReading(int value) {
        readings.add(new SensorReading(value));
        cleanOldReadings();
    }

    /**
     * 최근 24시간 동안 측정값이 건조 임계값 이상으로 안정적으로 유지되었는지 판단한다.
     * 조건 1: 최근 AppConfig.STABLE_HOURS 이내의 데이터가 존재할 것
     * 조건 2: 측정값 변동 폭이 AppConfig.VALUE_TOLERANCE 이내일 것 (안정)
     * 조건 3: 모든 측정값이 dryThreshold 이하일 것 (건조)
     *
     * @param dryThreshold 건조 판단 기준 수분 % (AppConfig.DRY_THRESHOLD에서 제공)
     * @return 관수가 필요한 상태이면 true
     */
    public synchronized boolean isStableAndDry(int dryThreshold) {
        List<SensorReading> recent = getRecentReadings();
        if (recent.isEmpty())
            return false;

        // "24시간 유지" 판단을 위해 최소 (STABLE_HOURS / CHECK_INTERVAL_HOURS)개의
        // 이력이 누적되어야 한다. 이력이 부족하면 아직 판단할 수 없다고 간주한다.
        int requiredReadings = AppConfig.STABLE_HOURS / AppConfig.CHECK_INTERVAL_HOURS;
        if (recent.size() < requiredReadings)
            return false;

        int min = recent.get(0).getValue();
        int max = recent.get(0).getValue();
        for (SensorReading r : recent) {
            if (r.getValue() < min)
                min = r.getValue();
            if (r.getValue() > max)
                max = r.getValue();
        }

        boolean isStable = (max - min) <= AppConfig.VALUE_TOLERANCE;
        boolean isDry = max <= dryThreshold;

        return isStable && isDry;
    }

    /**
     * 물을 준 후 이력을 초기화한다.
     * 알림이 재발송되지 않도록 물 주기 직후에 호출한다.
     */
    public synchronized void reset() {
        readings.clear();
    }

    /**
     * 가장 최근에 측정된 센서값을 반환한다.
     *
     * @return 최신 수분 %, 이력이 없으면 -1
     */
    public synchronized int getLatestValue() {
        if (readings.isEmpty())
            return -1;
        return readings.get(readings.size() - 1).getValue();
    }

    // ── private
    // ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

    /** AppConfig.STABLE_HOURS 이내의 측정값 목록을 반환한다. (호출부가 이미 synchronized) */
    private List<SensorReading> getRecentReadings() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(AppConfig.STABLE_HOURS);
        List<SensorReading> recent = new ArrayList<>();
        for (SensorReading r : readings) {
            if (r.getTimestamp().isAfter(cutoff))
                recent.add(r);
        }
        return recent;
    }

    /**
     * AppConfig.STABLE_HOURS 를 초과한 오래된 기록을 제거해 메모리를 절약한다. (호출부가 이미 synchronized)
     */
    private void cleanOldReadings() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(AppConfig.STABLE_HOURS);
        readings.removeIf(r -> r.getTimestamp().isBefore(cutoff));
    }

    // ── SensorReading (inner class)
    // ────────────────────────────────────────────────────────────────────────────────────────────────────────────
    /**
     * 센서에서 읽은 단일 측정값과 측정 시각을 저장하는 데이터 클래스.
     * SensorHistory의 구현 세부사항이므로 외부에 노출하지 않도록 했습니다.
     */
    private static class SensorReading {

        private final int value;
        private final LocalDateTime timestamp;

        SensorReading(int value) {
            this.value = value;
            this.timestamp = LocalDateTime.now();
        }

        int getValue() {
            return value;
        }

        LocalDateTime getTimestamp() {
            return timestamp;
        }
    }

}
