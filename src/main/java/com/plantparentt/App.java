package com.plantparentt;

import com.plantparentt.service.Hub;
import com.plantparentt.ui.ControlUI;

import javax.swing.*;

/**
 * @FileName : App.java
 * @Description : 식집사 애플리케이션 진입점
 *              Hub(Singleton)를 초기화하고 GUI를 실행
 */
public class App {
    public static void main(String[] args) {

        // GUI는 EDT(이벤트 디스패치 스레드)에서 생성. main() 메서드는 메인 스레드에서 실행된다. 여기서 바로 GUI를 만들면 메인
        // 스레드에서 Swing 컴포넌트를 건드리게 되는데 invokeLater()는 "이 작업을 EDT에 올려서 나중에 실행" 하라는 의미이다!
        // 이렇게 하면 GUI 관련 작업을 EDT에서 안전하게 실행 가능하다

        SwingUtilities.invokeLater(() -> {
            Hub hub = Hub.getInstance();
            new ControlUI(hub); // hub를 주입받아 GUI 창 생성
        });
    }
}
