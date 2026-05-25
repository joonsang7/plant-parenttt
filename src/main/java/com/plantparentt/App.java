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

        // GUI는 EDT(이벤트 디스패치 스레드)에서 생성. EDT(이벤트 디스패치 스레드)란? 
        // Swing에서 GUI 관련 작업(컴포넌트 생성, 이벤트 처리 등)을 담당하는 특별한 스레드이다. 
        // 여러 스레드가 동시에 Swing 컴포넌트를 수정할 때 예기치 않은 동작이나 충돌이 발생할 수 있다. 따라서 Swing 컴포넌트와 관련된 작업은 반드시 EDT에서 실행해야 한다.
        // main() 메서드는 메인 스레드에서 실행된다. 여기서 바로 GUI를 만들면 메인 스레드에서 Swing 컴포넌트를 건드리게 되는데 invokeLater()는 "이 작업을 EDT에 올려서 나중에 실행" 하라는 의미이다!
        // 이렇게 하면 GUI 관련 작업을 EDT에서 안전하게 실행 가능하다.
    
        /**
         * EDT에서 GUI를 생성하는 이유! :
         * 1. 스레드 안전성: Swing 컴포넌트는 스레드 안전하지 않기 때문에, GUI 관련 작업은 반드시 EDT에서 실행해야 한다. invokeLater()를 사용하면 GUI 작업이 EDT에서 실행되도록 보장할 수 있습니다.
         * 2. 응답성 유지: GUI 작업이 메인 스레드에서 실행되면, 긴 작업이 GUI를 멈추게 할 수 있습니다. EDT에서 GUI를 생성하면, 메인 스레드는 다른 작업을 계속 수행할 수 있어 응답성을 유지할 수 있습니다.
         * 3. 일관된 GUI 상태: EDT에서 GUI를 생성하면, 모든 GUI 관련 작업이 동일한 스레드에서 실행되므로, GUI 상태가 일관되게 유지됩니다. 이는 예기치 않은 동시성 문제를 방지하는 데 도움이 된다.
         */

        SwingUtilities.invokeLater(() -> {
            Hub hub = Hub.getInstance();
            new ControlUI(hub); // hub를 주입받아 GUI 창 생성
        });
    }
}
