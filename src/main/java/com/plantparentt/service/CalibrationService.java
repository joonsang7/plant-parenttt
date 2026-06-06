package com.plantparentt.service;

import com.plantparentt.config.AppConfig;
import com.plantparentt.sensor.MoistureSensor;

import java.util.ArrayList;
import java.util.List;

/**
 * @FileName    : CalibrationService.java
 * @Description : 센서 보정 비즈니스 로직을 담당하는 클래스 (SRP)
 *                UI(CalibrationDialog)와 완전히 분리되어 순수 Java로만 구현된다.
 *                5초 간격으로 ADC 값을 읽어 안정 여부를 판단하고,
 *                최소 4회(20초) 안정 시 평균 ADC 값을 반환한다.
 *                12회(60초) 내에 안정되지 않으면 -1(타임아웃)을 반환한다.
 */
public class CalibrationService {

    /**
     * 매 측정 후 UI 갱신용 콜백 인터페이스 (SRP: UI 의존성을 역방향으로 주입)
     */
    @FunctionalInterface
    public interface ProgressListener {
        /**
         * @param checkNumber 현재까지 수행한 검사 횟수 (1부터 시작)
         * @param adcValue    이번에 읽은 ADC 값 (미수신이면 -1)
         */
        void onCheck(int checkNumber, int adcValue);
    }

    /**
     * 보정값의 유효성을 판단한다. (SRP: 보정 관련 로직은 CalibrationService가 담당)
     * 포화 ADC 값이 건조 ADC 값보다 AppConfig.CALIB_VALID_RATIO 이상 높아야 유효하다.
     *
     * @param dryValue 건조 기준 ADC 값
     * @param wetValue 포화 기준 ADC 값
     * @return 유효한 보정값이면 true
     */
    public boolean isCalibrationValid(int dryValue, int wetValue) {
        return wetValue >= 0 && wetValue > dryValue + dryValue * AppConfig.CALIB_VALID_RATIO;
    }

    /**
     * 센서 ADC 값이 안정될 때까지 주기적으로 측정한다.
     *
     * <ul>
     *   <li>AppConfig.CALIB_CHECK_INTERVAL_MS(5초) 간격으로 측정</li>
     *   <li>최근 AppConfig.CALIB_MIN_CHECKS(4)개 값의 범위가
     *       AppConfig.CALIB_ADC_TOLERANCE(50) 이하이면 안정으로 판단</li>
     *   <li>AppConfig.CALIB_MAX_CHECKS(12)회를 초과하면 타임아웃(-1 반환)</li>
     * </ul>
     *
     * @param sensor           ADC 값을 읽을 센서
     * @param progressListener 매 측정 후 호출되는 진행 상황 콜백 (null 허용)
     * @return 안정된 최근 값들의 평균 ADC, 타임아웃이면 -1
     * @throws InterruptedException 백그라운드 스레드가 취소된 경우
     */
    public int measureStableValue(MoistureSensor sensor, ProgressListener progressListener)
            throws InterruptedException {

        List<Integer> readings = new ArrayList<>();

        for (int i = 0; i < AppConfig.CALIB_MAX_CHECKS; i++) {
            Thread.sleep(AppConfig.CALIB_CHECK_INTERVAL_MS);

            int value    = sensor.readValue();
            int checkNum = i + 1;

            if (progressListener != null) {
                progressListener.onCheck(checkNum, value);
            }

            if (value < 0) continue; // 미수신 → 이번 횟수는 누적하지 않음

            readings.add(value);

            // 최소 횟수 이상 누적 & 최근 값들이 허용 오차 이내이면 완료
            if (readings.size() >= AppConfig.CALIB_MIN_CHECKS
                    && isStable(readings, AppConfig.CALIB_MIN_CHECKS, AppConfig.CALIB_ADC_TOLERANCE)) {
                return computeAverage(readings, AppConfig.CALIB_MIN_CHECKS);
            }
        }

        return -1; // 최대 검사 횟수 초과 → 타임아웃
    }

    // ── private 헬퍼 ─────────────────────────────────────────────

    /**
     * 리스트 끝의 최근 recentCount 개 값의 범위(max-min)가 tolerance 이하인지 확인한다.
     */
    private boolean isStable(List<Integer> readings, int recentCount, int tolerance) {
        int from = readings.size() - recentCount;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = from; i < readings.size(); i++) {
            int v = readings.get(i);
            if (v < min) min = v;
            if (v > max) max = v;
        }
        return (max - min) <= tolerance;
    }

    /**
     * 리스트 끝의 최근 recentCount 개 값의 평균을 반환한다.
     */
    private int computeAverage(List<Integer> readings, int recentCount) {
        int from = readings.size() - recentCount;
        long sum = 0;
        for (int i = from; i < readings.size(); i++) sum += readings.get(i);
        return (int) (sum / recentCount);
    }
}
