package com.plantparentt.service;

/**
 * @FileName    : PlantMonitorView.java
 * @Description : Hub이 UI 계층에 상태를 전달하기 위한 인터페이스
 *                Hub은 이 인터페이스에만 의존하고, 구체적인 UI 구현체(ControlUI)를 알지 못한다. (DIP)
 */
public interface PlantMonitorView {

    /**
     * 알림 메시지를 화면에 표시하는 메서드
     *
     * @param message 표시할 알림 메시지
     */
    void showNotification(String message);

    /**
     * 특정 핀의 화분 슬롯에 수분 퍼센트를 갱신하기 위한 메서드
     *
     * @param pin   갱신할 핀 번호
     * @param value 최신 수분 % (-1이면 이력 없음)
     */
    void refreshPotPanel(int pin, int value);

    /**
     * 건조 알림 발송 시 해당 슬롯을 빨간색으로 강조하는 메서드
     * Hub이 핀 번호를 직접 전달하므로 이름 문자열 파싱 없이 해당 슬롯만 처리하도록 한다
     *
     * @param pin 강조할 핀 번호
     */
    void markSlotDry(int pin);

    /**
     * 관수 감지 후 슬롯의 강조 색상을 원래대로 되돌리는 메서드
     *
     * @param pin 초기화할 핀 번호
     */
    void resetPotPanel(int pin);
}
