/**
 * @FileName    : Plant.java
 * @Description : 식물의 이름을 보관하는 클래스
 *                센서 및 이력 관리는 PlantPot이 담당한다.
 */
public class Plant {

    private final String name;

    /**
     * @param name 식물 이름 (빈 문자열이면 "내 식물"로 대체)
     */
    public Plant(String name) {
        this.name = (name == null || name.isBlank()) ? "내 식물" : name;
    }

    /** @return 식물 이름 */
    public String getName() { return name; }

    @Override
    public String toString() { return name; }
}
