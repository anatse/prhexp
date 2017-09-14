package ru.pharmrus.export;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AptekaPlusTest {
    private String url = "jdbc:Cache://localhost:1972/SAMPLES";
    private String driver = "com.intersys.jdbc.CacheDriver";
    private AptekaPlus aptekaPlus = MockAptekaPlus.createMock();

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void loadGoods() {
        List<Map<String, String>> maps = aptekaPlus.loadGoods();
        assertEquals (maps, MockAptekaPlus.getPreparedData());
    }

    @Test
    void loadQuery () {
        String query = AptekaPlus.loadQuery("Test");
        assertNotNull (query);
        assertEquals(query, "TestQuery");
    }

    @Test
    void toJson () {
        assertEquals (AptekaPlus.toJson(MockAptekaPlus.getPreparedData()), "[{\"RetailPrice\":\"249.42\",\"BarCode\":\"1111222333444\",\"OstFirst\":\"2\",\"TradeTech\":\"ХЗ\",\"ProducerFullName\":\"ОАО Нанофарм\",\"DrugsFullName\":\"Аспирин\",\"SupplierFullName\":\"Катрен\",\"MNN\":\"ацетилсалициловая кислота\",\"Ost\":\"1\",\"UnitFullName\":\"упаковка\",\"ProducerShortName\":\"Нанофарм\",\"DrugFullName\":\"Аспирин-С\",\"OstLast\":\"1\",\"DrugsShortName\":\"Аспирин\",\"Packaging\":\"упаковки\",\"ID\":\"10001\",\"UnitShortName\":\"упак.\"}]");
    }
}