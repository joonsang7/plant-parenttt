import javax.swing.*;

/**
 * @FileName    : App.java
 * @Description : 식집사 애플리케이션 진입점
 *                Hub(Singleton)를 초기화하고 GUI를 실행
 */
public class App {
	public static void main(String[] args) {
		
		 // GUI는 EDT(이벤트 디스패치 스레드)에서 생성
	    SwingUtilities.invokeLater(new Runnable() {
	        @Override
	        public void run() {
	            Hub hub = Hub.getInstance();
	            new ControlUI(hub); // hub를 주입받아 GUI 창 생성 
	        }
	    });
	}
}
