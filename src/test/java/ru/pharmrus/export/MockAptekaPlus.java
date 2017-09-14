package ru.pharmrus.export;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

public class MockAptekaPlus {
    private static List<Map<String, String>> preparedData = prepareGoodsValues();
    private static List<Map<String, String>> prepareGoodsValues () {
        return Arrays.asList(
            toMap(
                v("ID", "10001"),
                v("BarCode", "1111222333444"),
                v("Ost", "1"),
                v("OstFirst", "2"),
                v("OstLast", "1"),
                v("DrugsFullName", "Аспирин"),
                v("DrugsShortName", "Аспирин"),
                v("UnitFullName", "упаковка"),
                v("UnitShortName", "упак."),
                v("MNN", "ацетилсалициловая кислота"),
                v("ProducerFullName", "ОАО Нанофарм"),
                v("ProducerShortName", "Нанофарм"),
                v("Packaging", "упаковки"),
                v("TradeTech", "ХЗ"),
                v("DrugFullName", "Аспирин-С"),
                v("SupplierFullName", "Катрен"),
                v("RetailPrice", "249.42")
            )
        );
    }

    public static List<Map<String, String>> getPreparedData () {
        return preparedData;
    }

    public static String[] v(String key, String value) {
        return new String[]{key, value};
    }


    public static Map<String, String> toMap (String[] ... mapvs) {
        Map<String, String> map = new HashMap<>();
        for (String[] mapv : mapvs) {
            map.put(mapv[0], mapv[1]);
        }

        return map;
    }

    public static AptekaPlus createMock () {
        AptekaPlus apmock = mock(AptekaPlus.class);

        // LoadGoods function
        when(apmock.loadGoods()).thenReturn(getPreparedData());

        return apmock;
    }
}
