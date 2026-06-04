package com.plantparentt.sensor;

/**
 * @FileName    : MoistureSensor.java
 * @Description : 토양 수분 센서의 공통 인터페이스
 *                ArduinoMoistureSensor 구체 클래스가 해당 인터페이스를 구현합니다
 */
public interface MoistureSensor {

    /**
     * 토양 수분 ADC 값을 반환하는 메서드
     *
     * @return 0~1023 범위의 ADC 값, 미수신이면 -1
     */
    int readValue();

    /**
     * 센서가 화분 흙에 꽂혀 있는지 판단하는 메서드
     *
     * @return 센서가 삽입되어 있으면 true, 공기 중이거나 미연결이면 false
     */
    boolean isInserted();

    /**
     * 공기 중(건조 기준)과 물 속(포화 기준) ADC 값으로 센서를 보정하는 메서드
     * 이후 getMoisturePercent()가 0~100% 값을 반환하도록 보정됩니다
     *
     * @param dryValue 공기 중 ADC 값 (0% 기준)
     * @param wetValue 물 속 ADC 값  (100% 기준)
     */
    void setCalibration(int dryValue, int wetValue);

    /**
     * 보정값을 적용한 수분 퍼센트를 반환하는 메서드
     *
     * @return 0~100 범위의 수분 %, 보정 전이거나 미수신이면 -1
     */
    int getMoisturePercent();

    /**
     * MQTT 브로커에 연결된 상태인지 반환하는 메서드
     *
     * @return 연결 중이면 true, 연결 실패 또는 해제 상태이면 false
     */
    boolean isConnected();

    /**
     * MQTT 연결을 해제하는 메서드
     * 식물 생성 취소 또는 실패 시 중복 구독을 방지하기 위해 호출
     */
    void disconnect();
}
