package com.plantparentt.model;

import com.plantparentt.config.AppConfig;
import com.plantparentt.sensor.MoistureSensor;
import com.plantparentt.sensor.SensorHistory;

/**
 * @FileName    : PlantPot.java
 * @Description : 물리적 화분 한 개를 표현하는 클래스
 *                아두이노 핀 번호, 식물 이름, 센서, 센서 이력을 하나로 묶는다
 *                핀 번호(0~5)는 화분의 고유 식별자 역할을 하게 됩니다
 *
 *                Hub에서 각 화분의 상태를 체크할 때 updateSensorData()로 센서 데이터를 갱신한 뒤
 *                needsWatering()으로 건조 여부를 판단하고 관수 감지 후에는 resetAfterWatering()으로 이력과 알림 상태를 초기화합니다
 */
public class PlantPot {

    private final int pinNumber;                // 아두이노 아날로그 핀 번호 (0 = A0)
    private final String plantName;             // 식물 이름 (공백이면 "내 식물"로 대체)
    private final MoistureSensor sensor;
    private final SensorHistory history;
    private volatile boolean notified = false; // 관수 알림 발송 여부 ( volaile로 쓴 이유는 일정 주기마다 값을 체크하는 스케줄러와 수동 "수분량 체크" 버튼이 동시에 접근하는 상황을 생각해서 안전하게 최신값이 반영되도록 하기 위해서입니다 )

    /**
     * 생성자. 아두이노 핀 번호, 식물 이름, 센서 객체를 받아 초기화합니다
     * 
     * @param pinNumber 아두이노 아날로그 핀 번호 (0~5)
     * @param plantName 이 화분에 심긴 식물 이름 (null·공백이면 "내 식물"로 대체)
     * @param sensor    이 화분에 꽂힌 센서 객체
     */
    public PlantPot(int pinNumber, String plantName, MoistureSensor sensor) {
        this.pinNumber  = pinNumber;
        this.plantName  = (plantName == null || plantName.isBlank()) ? "내 식물" : plantName;
        this.sensor     = sensor;
        this.history    = new SensorHistory();
    }

    /**
     * 센서 삽입 여부를 확인한 뒤 수분 퍼센트를 이력에 추가하는 메서드
     * 센서가 꽂혀있지 않거나 보정 전이면 측정을 건너뛰고 경고 메시지를 출력하도록 했습니다
     */
    public void updateSensorData() {
        if (!sensor.isInserted()) {
            System.out.println("[경고] " + plantName
                + " 센서가 화분에 꽂혀있지 않습니다. (A" + pinNumber + ")");
            return;
        }
        int percent = sensor.getMoisturePercent();
        if (percent < 0) {
            System.out.println("[경고] " + plantName + " 센서 보정값이 없습니다.");
            return;
        }
        history.addReading(percent);
        System.out.println("[A" + pinNumber + "] " + plantName + " 수분: " + percent + "%");
    }

    /**
     * 현재 화분이 관수가 필요한 상태인지 판단하는 메서드
     *
     * @return 수분이 AppConfig.DRY_THRESHOLD 이하로 AppConfig.STABLE_HOURS 시간 유지되면 true
     */
    public boolean needsWatering() {
        return history.isStableAndDry(AppConfig.DRY_THRESHOLD);
    }

    /**
     * 센서에서 현재 수분 %를 직접 읽어 반환하는 메서드 (보정값이 적용된 퍼센트)
     * 관수 감지 전용 단기 스케줄(수분량 체크)에서 최신값을 확인할 때 사용합니다
     *
     * @return 현재 수분 % (0~100), 미보정·미수신이면 -1
     */
    public int getCurrentSensorPercent() {
        return sensor.getMoisturePercent();
    }

    /**
     * 현재 수분값이 즉시 건조 기준 이하인지 판단하는 메서드
     * needsWatering()과 달리 24시간 유지 조건 없이 최신 값을 통해 판단하기 때문에 수동 체크(수분량 체크 버튼)시 즉시 알림에 사용됩니다
     *
     * @return 최신 수분 %가 AppConfig.DRY_THRESHOLD 미만 (건조 상태) 이면 true
     */
    public boolean isCurrentlyDry() {
        int latest = history.getLatestValue();
        return latest >= 0 && latest < AppConfig.DRY_THRESHOLD;
    }

    /**
     * 관수 감지 후 이력과 알림 상태를 초기화하는 메서드
     * Hub의 checkPot()에서 관수가 감지되면 자동으로 호출됩니다
     * 
     * 관수 감지 이후 이력 초기화하여 관수 알림이 재발송되지 않도록 했습니다
     */
    public void resetAfterWatering() {
        history.reset();
        notified = false;
        System.out.println("[" + plantName + "] 관수 감지, 이력 초기화.");
    }

    /**
     * 센서 MQTT 연결을 해제하는 메서드
     * 화분 삭제 시 Hub에서 호출하여 중복 구독을 방지
     */
    public void disconnect() {
        sensor.disconnect();
    }


    

    // ── Getters ───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

    /** @return 아두이노 아날로그 핀 번호 */
    public int getPinNumber() { return pinNumber; }

    /** @return 식물 이름 */
    public String getPlantName() { return plantName; }

    /** @return 가장 최근 수분 %, 이력 없으면 -1 */
    public int getLatestValue() { return history.getLatestValue(); }

    /** @return 관수 알림이 이미 발송되었으면 true */
    public boolean isNotified() { return notified; }

    /** @param notified 알림 발송 여부 설정 */
    public void setNotified(boolean notified) { this.notified = notified; }
}
