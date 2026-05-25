package com.plantparentt.model;

import com.plantparentt.config.AppConfig;
import com.plantparentt.sensor.MoistureSensor;
import com.plantparentt.sensor.SensorHistory;

/**
 * @FileName    : PlantPot.java
 * @Description : 물리적 화분 한 개를 표현하는 클래스
 *                아두이노 핀 번호, 식물 정보, 센서, 센서 이력을 하나로 묶는다
 *                핀 번호(0~5)가 화분의 고유 식별자 역할을 함
 */
public class PlantPot {



    private final int pinNumber;          // 아두이노 아날로그 핀 번호 (0 = A0)
    private final Plant plant;
    private final MoistureSensor sensor;
    private final SensorHistory history;
    private volatile boolean notified = false; // 관수 알림 발송 여부 (스케줄러·EDT 양쪽에서 접근 — volatile로 가시성 보장)

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
     * 센서 삽입 여부를 확인한 뒤 수분 퍼센트를 이력에 추가
     * 센서가 꽂혀있지 않거나 보정 전이면 측정을 건너뛴다.
     */
    public void updateSensorData() {
        if (!sensor.isInserted()) {
            System.out.println("[경고] " + plant.getName()
                + " 센서가 화분에 꽂혀있지 않습니다. (A" + pinNumber + ")");
            return;
        }
        int percent = sensor.getMoisturePercent();
        if (percent < 0) {
            System.out.println("[경고] " + plant.getName() + " 센서 보정값이 없습니다.");
            return;
        }
        history.addReading(percent);
        System.out.println("[A" + pinNumber + "] " + plant.getName()
            + " 수분: " + percent + "%");
    }

    /**
     * 현재 화분이 관수가 필요한 상태인지 판단
     *
     * @return 수분이 AppConfig.DRY_THRESHOLD 이하로 AppConfig.STABLE_HOURS 시간 유지되면 true
     */
    public boolean needsWatering() {
        return history.isStableAndDry(AppConfig.DRY_THRESHOLD);
    }

    /**
     * 센서에서 현재 수분 %를 직접 읽어 반환한다. (이력에는 추가하지 않음)
     * 관수 감지 전용 단기 스케줄에서 이력 오염 없이 최신값을 확인할 때 사용한다.
     *
     * @return 현재 수분 % (0~100), 미보정·미수신이면 -1
     */
    public int getCurrentSensorPercent() {
        return sensor.getMoisturePercent();
    }

    /**
     * 현재 수분값이 즉시 건조 기준 이하인지 판단
     * needsWatering()과 달리 24시간 유지 조건 없이 최신 값을 통해 판단한다. 수동 체크(수분량 체크 버튼) 시 즉시 알림에 사용됩니다.
     * 수동 체크(수분량 체크 버튼) 시 즉시 알림에 사용된다.
     *
     * @return 최신 수분 %가 AppConfig.DRY_THRESHOLD 미만이면 true
     */
    public boolean isCurrentlyDry() {
        int latest = history.getLatestValue();
        return latest >= 0 && latest < AppConfig.DRY_THRESHOLD;
    }

    /**
     * 관수 감지 후 이력과 알림 상태를 초기화한다.
     * Hub의 checkPot()에서 관수가 감지되면 자동으로 호출된다.
     */
    public void resetAfterWatering() {
        history.reset();
        notified = false;
        System.out.println("[" + plant.getName() + "] 관수 감지, 이력 초기화.");
    }

    /**
     * 센서 MQTT 연결을 해제한다.
     * 화분 삭제 시 Hub에서 호출하여 중복 구독을 방지한다.
     */
    public void disconnect() {
        sensor.disconnect();
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
