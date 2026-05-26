package com.plantparentt;

import com.plantparentt.config.AppConfig;
import com.plantparentt.service.Hub;
import com.plantparentt.ui.ControlUI;

import javax.swing.*;

/**
 * @FileName    : App.java
 * @Description : 식집사 애플리케이션 진입점
 *                Hub(Singleton)를 초기화하고 GUI를 실행한다.
 *
 *                앱 시작 시 MQTT 브로커 연결 가능 여부를 메인 스레드에서 먼저 확인한다.
 *                브로커가 응답하지 않으면 오류 팝업을 띄우고 종료한다.
 *                확인 자체를 EDT 안에서 하면 네트워크 대기(최대 3초) 동안 UI 전체가 멈추기 때문에
 *                네트워크 체크는 메인 스레드에서, GUI 생성만 EDT에서 실행한다.
 */
public class App {
    public static void main(String[] args) {

        // ── ① 브로커 연결 확인 (메인 스레드) ─────────────────────────────────────
        // EDT가 아닌 메인 스레드에서 실행해야 네트워크 대기 시 UI가 멈추지 않는다
        if (!Hub.isBrokerReachable()) {
            JOptionPane.showMessageDialog(
                null,
                "MQTT 브로커에 연결할 수 없습니다.\n"
                    + "브로커 IP : " + AppConfig.BROKER_IP + "\n"
                    + "포트      : " + AppConfig.BROKER_PORT + "\n\n"
                    + "Mosquitto가 실행 중인지 확인해 주세요.",
                "브로커 연결 실패",
                JOptionPane.ERROR_MESSAGE
            );
            return; // GUI 열지 않고 종료
        }

        // ── ② GUI 실행 (EDT) ──────────────────────────────────────────────────────
        // Swing 컴포넌트는 반드시 EDT에서 생성해야 스레드 안전성이 보장된다
        SwingUtilities.invokeLater(() -> {
            Hub hub = Hub.getInstance();
            new ControlUI(hub);
        });
    }
}
