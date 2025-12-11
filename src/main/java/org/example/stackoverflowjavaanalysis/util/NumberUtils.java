package org.example.stackoverflowjavaanalysis.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 数值处理工具类
 */
public class NumberUtils {

    /**
     * 将double值四舍五入保留两位小数
     *
     * @param value 待处理的double值
     * @return 保留两位小数的double值
     */
    public static double roundToTwoDecimalPlaces(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        BigDecimal bd = new BigDecimal(Double.toString(value));
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    /**
     * 将float值四舍五入保留两位小数
     *
     * @param value 待处理的float值
     * @return 保留两位小数的double值
     */
    public static double roundToTwoDecimalPlaces(float value) {
        return roundToTwoDecimalPlaces((double) value);
    }
}