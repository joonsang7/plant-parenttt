import javax.swing.*;

/**
 * @FileName    : App.java
 * @Description : 식집사 애플리케이션 진입점
 *                Hub(Singleton)를 초기화하고 GUI를 실행한다.
 */
public class App {
    public static void main(String[] args) {
        // GUI는 반드시 EDT(이벤트 디스패치 스레드)에서 생성
        SwingUtilities.invokeLater(() -> {
            Hub hub = Hub.getInstance();
            new ControlUI(hub);
        });
    }
}
