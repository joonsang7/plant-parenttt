/**
 * @FileName    : Plant.java
 * @Description : 식물의 이름과 종(PlantSpecies) 정보를 보관하는 클래스
 *                센서 및 이력 관리는 PlantPot이 담당한다.
 */
public class Plant {

    private final String name;
    private final PlantSpecies species;

    /**
     * @param species 식물 종 (필수)
     * @param name    식물 이름 (선택, 빈 문자열이면 종 이름으로 대체)
     */
    public Plant(PlantSpecies species, String name) {
        this.species = species;
        this.name    = (name == null || name.isBlank()) ? species.getDisplayName() : name;
    }

    /** @return 식물 이름 */
    public String getName() { return name; }

    /** @return 식물 종 */
    public PlantSpecies getSpecies() { return species; }

    @Override
    public String toString() {
        return name + " (" + species.getDisplayName() + ")";
    }
}
